package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.FlipAction;
import com.osrsscripts.core.ge.FlipEngine;
import com.osrsscripts.core.ge.FlipScanner;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.model.AccountState;
import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.VolumePoint;
import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.prices.WikiPriceClient;
import com.osrsscripts.core.task.Task;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One flip tick: read the live account and market, rank candidates, decide the Grand Exchange
 * actions, and execute them. Runs only while the GE is open. A transient market-fetch failure
 * skips the tick rather than killing the script.
 */
public final class FlipTask implements Task {

    private final GeClient client;
    private final WikiPriceClient prices;
    private final FlipScanner scanner;
    private final FlipEngine engine;
    private final GeTax tax;
    private final BuyLimitLedger ledger;
    private final FlipConfig config;
    private final FlipActionExecutor executor;

    public FlipTask(GeClient client, WikiPriceClient prices, FlipScanner scanner, FlipEngine engine,
                    GeTax tax, BuyLimitLedger ledger, FlipConfig config, FlipActionExecutor executor) {
        this.client = Objects.requireNonNull(client, "client");
        this.prices = Objects.requireNonNull(prices, "prices");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.tax = Objects.requireNonNull(tax, "tax");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public boolean shouldRun() {
        return client.isOpen();
    }

    @Override
    public void execute() {
        Map<Integer, ItemMeta> mapping;
        Map<Integer, PricePoint> latest;
        Map<Integer, VolumePoint> volumes;
        try {
            mapping = prices.mapping();
            latest = prices.latest();
            volumes = prices.volumesOneHour();
        } catch (IOException e) {
            // Transient market-data failure: skip this tick, try again next loop.
            return;
        }

        AccountState account = new AccountState(client.coins(), client.offers(), client.stock());
        List<FlipCandidate> candidates = scanner.scan(mapping, latest, volumes, config, tax);
        List<FlipAction> actions = engine.decide(candidates, latest, account, ledger, config, Instant.now());
        executor.execute(actions);
    }
}
