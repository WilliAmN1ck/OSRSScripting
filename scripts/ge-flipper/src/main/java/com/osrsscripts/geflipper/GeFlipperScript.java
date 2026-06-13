package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.FlipEngine;
import com.osrsscripts.core.ge.FlipScanner;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.ge.GeTaxRules;
import com.osrsscripts.core.ge.IdleReason;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.ge.TradeHistory;
import com.osrsscripts.core.humanize.AfkScheduler;
import com.osrsscripts.core.humanize.DelayDistribution;
import com.osrsscripts.core.humanize.FatigueScaler;
import com.osrsscripts.core.humanize.FidgetSelector;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.ItemMeta;
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
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final Duration PLACEMENT_RETRY_BACKOFF = Duration.ofSeconds(10);
    private static final long FIDGET_MIN_MS = 15_000L;
    private static final long FIDGET_MAX_MS = 45_000L;
    private static final Duration FATIGUE_RAMP = Duration.ofHours(3);
    private static final double FATIGUE_MAX = 1.6;
    private static final long CADENCE_MIN_MS = 1_500L;
    private static final long CADENCE_MAX_MS = 3_500L;
    private static final long REACTION_MIN_MS = 200L;
    private static final long REACTION_MAX_MS = 800L;
    private static final Duration AFK_MIN_GAP = Duration.ofMinutes(12);
    private static final Duration AFK_MAX_GAP = Duration.ofMinutes(24);
    private static final Duration AFK_MIN = Duration.ofSeconds(20);
    private static final Duration AFK_MAX = Duration.ofSeconds(90);

    @Override
    public void execute(ScriptContext context) {
        GeClient client = new SdkGeClient();
        WikiPriceClient prices = WikiPriceClient.live(USER_AGENT);
        FlipScanner scanner = new FlipScanner();
        FlipEngine engine = new FlipEngine();
        GeTax tax = new GeTax(GeTaxRules.defaults());
        BuyLimitLedger ledger = new BuyLimitLedger();
        StockLedger stock = new StockLedger();
        TradeHistory history = new TradeHistory();
        OfferTracker tracker = new OfferTracker(ledger, stock, history);

        Path stateFile = ScriptSettings.getDefault().getDirectory().toPath().resolve(STATE_FILE);
        StateStore store = new StateStore(stateFile);
        PersistedState loaded = store.load();
        StateMapper.restore(loaded, ledger, stock, tracker, history);
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
        AtomicBoolean clearHistoryRequest = new AtomicBoolean(false);
        FlipperPanel panel = new FlipperPanel(config.get(), config::set,
                () -> clearHistoryRequest.set(true));
        context.getSidebar().addSidebarTab(TAB_NAME, null, panel);

        // Echo's built-in action humanization, plus our own idle fidgets while slots sit full.
        Antiban.setScriptAiAntibanEnabled(true);
        Random random = new Random();
        Instant startedAt = Instant.now();
        FatigueScaler fatigue = new FatigueScaler(startedAt, FATIGUE_RAMP, FATIGUE_MAX);
        SdkFidget fidget = new SdkFidget(context, random);
        HumanizedIdle idle = new HumanizedIdle(
                new DelayDistribution(FIDGET_MIN_MS, FIDGET_MAX_MS, random),
                new FidgetSelector(random), fatigue, fidget::run);

        // A short, fatigue-scaled human pause before each batch of GE actions.
        DelayDistribution reactionDelay = new DelayDistribution(REACTION_MIN_MS, REACTION_MAX_MS,
                random);
        FlipActionExecutor executor = new FlipActionExecutor(client, () -> context.getWaiting()
                .sleep(Math.round(reactionDelay.nextMs() * fatigue.multiplierAt(Instant.now()))));
        // Varied loop cadence and occasional look-away AFKs (distinct from client breaks).
        DelayDistribution cadence = new DelayDistribution(CADENCE_MIN_MS, CADENCE_MAX_MS, random);
        AfkScheduler afk = new AfkScheduler(startedAt, random, AFK_MIN_GAP, AFK_MAX_GAP, AFK_MIN,
                AFK_MAX);

        FlipTask flipTask = new FlipTask(client, prices, scanner, engine, tax, ledger, stock,
                tracker, history, config::get, executor, persister, PLACEMENT_RETRY_BACKOFF, idle,
                clearHistoryRequest);
        List<Task> tasks = List.of(
                new BreakIdleTask(new SdkBreakSource(context.getSidecars())),
                flipTask);
        TaskRunner runner = new TaskRunner(tasks);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                Instant now = Instant.now();
                Optional<Duration> lookAway = afk.pollAt(now);
                if (lookAway.isPresent()) {
                    // Attention wandered: idle for the look-away, but in chunks so a Stop (and the
                    // final save) is not held up for the whole 20-90s.
                    long remaining = lookAway.get().toMillis();
                    while (remaining > 0 && !Thread.currentThread().isInterrupted()) {
                        long chunk = Math.min(remaining, 1_000L);
                        context.getWaiting().sleep(chunk);
                        remaining -= chunk;
                    }
                    continue;
                }
                runner.tick();
                refreshStats(panel, client, tracker, history, prices, profitBaseline, startedAt,
                        context, flipTask.idleReason());
                context.getWaiting().sleep(Math.round(cadence.nextMs() * fatigue.multiplierAt(now)));
            }
        } finally {
            // Save first: a failure tearing down the sidebar must not cost us the final state.
            persister.accept(StateMapper.snapshot(ledger, stock, tracker, history, config.get()));
            context.getSidebar().removeSidebarTab(TAB_NAME);
        }
    }

    /** Stats are a display nicety: a failed client read must never kill the loop. */
    private static void refreshStats(FlipperPanel panel, GeClient client, OfferTracker tracker,
                                     TradeHistory history, WikiPriceClient prices,
                                     long profitBaseline, Instant startedAt,
                                     ScriptContext context, IdleReason idleReason) {
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
                    offerLines,
                    tradeRows(history, prices),
                    idleReason));
        } catch (RuntimeException e) {
            context.getLogger().warn("Skipping stats refresh", e);
        }
    }

    private static List<StatsSnapshot.TradeRow> tradeRows(TradeHistory history,
                                                          WikiPriceClient prices) {
        Map<Integer, ItemMeta> mapping;
        try {
            mapping = prices.mapping(); // long-lived cache; a miss only degrades names to ids
        } catch (IOException e) {
            mapping = Map.of();
        }
        List<StatsSnapshot.TradeRow> rows = new ArrayList<>();
        for (TradeHistory.ItemRecord record : history.records()) {
            ItemMeta meta = mapping.get(record.itemId());
            String name = meta != null ? meta.name() : "#" + record.itemId();
            rows.add(new StatsSnapshot.TradeRow(name, record.netProfit(),
                    record.flipsCompleted(), record.qtySold()));
        }
        return rows;
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
                .avoidAfterLossGp(1_000L)
                .build();
    }
}
