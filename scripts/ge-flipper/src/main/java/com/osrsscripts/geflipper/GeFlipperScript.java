package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.FlipEngine;
import com.osrsscripts.core.ge.FlipScanner;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.ge.GeTaxRules;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.prices.WikiPriceClient;
import com.osrsscripts.core.task.Task;
import com.osrsscripts.core.task.TaskRunner;
import java.time.Duration;
import java.util.List;
import org.tribot.automation.TribotScript;
import org.tribot.automation.script.ScriptContext;

/**
 * Entry point for the Grand Exchange flipper. Each loop drives a {@link TaskRunner} that first
 * opens the GE if needed, then runs one flip tick: read live account/market state, rank
 * candidates, decide actions, and execute them against the game client.
 *
 * <p>The flipping logic lives in {@code libraries:core}; this class only wires it to the TRiBot
 * Script SDK via {@link SdkGeClient}. Configuration is hardcoded for now — an in-client config
 * panel ({@code ScriptContext.sidebar}) and persistence replace it in a later step.
 */
public final class GeFlipperScript implements TribotScript {

    private static final String USER_AGENT = "osrs-scripts-suite/ge-flipper";
    private static final long TICK_INTERVAL_MS = 2_000L;

    @Override
    public void execute(ScriptContext context) {
        GeClient client = new SdkGeClient();
        WikiPriceClient prices = WikiPriceClient.live(USER_AGENT);
        FlipScanner scanner = new FlipScanner();
        FlipEngine engine = new FlipEngine();
        GeTax tax = new GeTax(GeTaxRules.defaults());
        BuyLimitLedger ledger = new BuyLimitLedger();
        StockLedger stock = new StockLedger();
        OfferTracker tracker = new OfferTracker(ledger, stock, tax);
        FlipConfig config = defaultConfig();
        FlipActionExecutor executor = new FlipActionExecutor(client);

        List<Task> tasks = List.of(
                new EnsureGeOpenTask(client),
                new FlipTask(client, prices, scanner, engine, tax, ledger, stock, tracker,
                        () -> config, executor, state -> { }));
        TaskRunner runner = new TaskRunner(tasks);

        while (!Thread.currentThread().isInterrupted()) {
            runner.tick();
            context.getWaiting().sleep(TICK_INTERVAL_MS);
        }
    }

    /**
     * Provisional run parameters until the config panel exists. {@code maxOfferAge} is inert this
     * phase because the SDK exposes no offer placement timestamp (see the Phase 3b handoff).
     */
    private static FlipConfig defaultConfig() {
        return FlipConfig.builder()
                .capitalCap(1_000_000L)
                .perItemCapitalCap(250_000L)
                .minMarginGp(5L)
                .minMarginPct(0.01)
                .minVolume(1_000L)
                .maxSlots(4)
                .maxOfferAge(Duration.ofMinutes(30))
                .build();
    }
}
