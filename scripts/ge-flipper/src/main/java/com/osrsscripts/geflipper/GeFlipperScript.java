package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.FlipEngine;
import com.osrsscripts.core.ge.FlipScanner;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.ge.GeTaxRules;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.humanize.DelayDistribution;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.persistence.PersistedState;
import com.osrsscripts.core.persistence.StateMapper;
import com.osrsscripts.core.persistence.StateStore;
import com.osrsscripts.core.prices.WikiPriceClient;
import com.osrsscripts.core.task.Task;
import com.osrsscripts.core.task.TaskRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.tribot.automation.TribotScript;
import org.tribot.automation.script.ScriptContext;
import org.tribot.script.sdk.antiban.Antiban;
import org.tribot.script.sdk.util.ScriptSettings;

/**
 * Entry point for the Grand Exchange flipper. Each loop drives a {@link TaskRunner} that idles
 * during client-scheduled breaks, then runs one flip tick: observe offers, read the market,
 * decide actions, and execute them against the game client (opening the GE only when a tick
 * actually has actions to perform).
 *
 * <p>The flipping logic lives in {@code libraries:core}; this class is the composition root that
 * wires it to the TRiBot SDK: {@link SdkGeClient} for the GE, a {@link FlipperPanel} sidebar tab
 * for config + stats, {@link SdkBreakSource} for breaks, and a {@link StateStore} in the
 * script-settings directory so ledgers, offer stamps, and profit survive restarts.
 */
public final class GeFlipperScript implements TribotScript {

    private static final String USER_AGENT = "osrs-scripts-suite/ge-flipper";
    private static final String TAB_NAME = "GE Flipper";
    private static final String STATE_FILE = "ge-flipper-state.json";
    private static final long TICK_INTERVAL_MS = 2_000L;
    private static final Duration PLACEMENT_RETRY_BACKOFF = Duration.ofSeconds(10);
    private static final long FIDGET_MIN_MS = 15_000L;
    private static final long FIDGET_MAX_MS = 45_000L;

    @Override
    public void execute(ScriptContext context) {
        GeClient client = new SdkGeClient();
        WikiPriceClient prices = WikiPriceClient.live(USER_AGENT);
        FlipScanner scanner = new FlipScanner();
        FlipEngine engine = new FlipEngine();
        GeTax tax = new GeTax(GeTaxRules.defaults());
        BuyLimitLedger ledger = new BuyLimitLedger();
        StockLedger stock = new StockLedger();
        OfferTracker tracker = new OfferTracker(ledger, stock);
        FlipActionExecutor executor = new FlipActionExecutor(client);

        Path stateFile = ScriptSettings.getDefault().getDirectory().toPath().resolve(STATE_FILE);
        StateStore store = new StateStore(stateFile);
        PersistedState loaded = store.load();
        StateMapper.restore(loaded, ledger, stock, tracker);
        long profitBaseline = loaded.realizedProfit();
        Consumer<PersistedState> persister = state -> {
            try {
                store.save(state);
            } catch (IOException e) {
                context.getLogger().warn("Failed to save flipper state to " + stateFile, e);
            }
        };

        FlipConfig restoredConfig = StateMapper.restoredConfig(loaded);
        AtomicReference<FlipConfig> config =
                new AtomicReference<>(restoredConfig != null ? restoredConfig : defaultConfig());
        FlipperPanel panel = new FlipperPanel(config.get(), config::set);
        context.getSidebar().addSidebarTab(TAB_NAME, null, panel);

        // Echo's built-in action humanization, plus our own idle fidgets while slots sit full.
        Antiban.setScriptAiAntibanEnabled(true);
        Random random = new Random();
        IdleBehavior idle = new HumanizedIdle(
                new DelayDistribution(FIDGET_MIN_MS, FIDGET_MAX_MS, random),
                new SdkFidget(context, random));

        List<Task> tasks = List.of(
                new BreakIdleTask(new SdkBreakSource(context.getSidecars())),
                new FlipTask(client, prices, scanner, engine, tax, ledger, stock, tracker,
                        config::get, executor, persister, PLACEMENT_RETRY_BACKOFF, idle));
        TaskRunner runner = new TaskRunner(tasks);

        Instant startedAt = Instant.now();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                runner.tick();
                refreshStats(panel, client, tracker, profitBaseline, startedAt, context);
                context.getWaiting().sleep(TICK_INTERVAL_MS);
            }
        } finally {
            // Save first: a failure tearing down the sidebar must not cost us the final state.
            persister.accept(StateMapper.snapshot(ledger, stock, tracker, config.get()));
            context.getSidebar().removeSidebarTab(TAB_NAME);
        }
    }

    /** Stats are a display nicety: a failed client read must never kill the loop. */
    private static void refreshStats(FlipperPanel panel, GeClient client, OfferTracker tracker,
                                     long profitBaseline, Instant startedAt,
                                     ScriptContext context) {
        try {
            List<String> offerLines = new ArrayList<>();
            for (GeOffer offer : client.offers()) {
                if (!offer.isEmpty()) {
                    offerLines.add(offer.slot() + " " + offer.side() + " item " + offer.itemId()
                            + " " + offer.filled() + "/" + offer.quantity()
                            + " @ " + offer.pricePerItem());
                }
            }
            panel.update(new StatsSnapshot(
                    Duration.between(startedAt, Instant.now()),
                    tracker.realizedProfit() - profitBaseline,
                    tracker.realizedProfit(),
                    tracker.flipsCompleted(),
                    client.coins(),
                    offerLines));
        } catch (RuntimeException e) {
            context.getLogger().warn("Skipping stats refresh", e);
        }
    }

    /**
     * First-run parameters, used only when no persisted config exists. {@code capitalCap = 0}
     * disables buying, so a fresh install idles until the user sets a capital cap in the sidebar
     * — it must never start trading (or hunting members items on an F2P world) on guesses.
     */
    private static FlipConfig defaultConfig() {
        return FlipConfig.builder()
                .capitalCap(0L)
                .perItemCapitalCap(250_000L)
                .minMarginGp(5L)
                .minMarginPct(0.01)
                .minVolume(1_000L)
                .minDeploymentGp(1_000L)
                .maxSlots(3)
                .maxOfferAge(Duration.ofMinutes(30))
                .sellExitAfterRelists(3)
                .build();
    }
}
