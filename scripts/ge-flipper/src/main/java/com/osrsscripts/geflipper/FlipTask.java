package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.FlipAction;
import com.osrsscripts.core.ge.FlipEngine;
import com.osrsscripts.core.ge.FlipScanner;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.model.AccountState;
import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.VolumePoint;
import com.osrsscripts.core.persistence.PersistedState;
import com.osrsscripts.core.persistence.StateMapper;
import com.osrsscripts.core.prices.WikiPriceClient;
import com.osrsscripts.core.task.Task;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One flip tick: observe the live offers (stamping placement times and recording fills via the
 * {@link OfferTracker}), read the market, rank candidates, decide the Grand Exchange actions, and
 * execute them. Only stock the flipper itself bought is offered for sale. Config is re-read every
 * tick so sidebar edits apply immediately; any tick that acted snapshots the persistable state.
 * Runs only while the GE is open. A transient market-fetch failure skips the tick rather than
 * killing the script.
 */
public final class FlipTask implements Task {

    private final GeClient client;
    private final WikiPriceClient prices;
    private final FlipScanner scanner;
    private final FlipEngine engine;
    private final GeTax tax;
    private final BuyLimitLedger ledger;
    private final StockLedger stock;
    private final OfferTracker tracker;
    private final Supplier<FlipConfig> config;
    private final FlipActionExecutor executor;
    private final Consumer<PersistedState> persister;

    public FlipTask(GeClient client, WikiPriceClient prices, FlipScanner scanner, FlipEngine engine,
                    GeTax tax, BuyLimitLedger ledger, StockLedger stock, OfferTracker tracker,
                    Supplier<FlipConfig> config, FlipActionExecutor executor,
                    Consumer<PersistedState> persister) {
        this.client = Objects.requireNonNull(client, "client");
        this.prices = Objects.requireNonNull(prices, "prices");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.tax = Objects.requireNonNull(tax, "tax");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.persister = Objects.requireNonNull(persister, "persister");
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

        Instant now = Instant.now();
        FlipConfig currentConfig = config.get();
        List<GeOffer> offers = tracker.observe(client.offers(), now);
        AccountState account = new AccountState(client.coins(), offers, sellableStock());
        List<FlipCandidate> candidates = scanner.scan(mapping, latest, volumes, currentConfig, tax);
        List<FlipAction> actions =
                engine.decide(candidates, latest, account, ledger, currentConfig, now);
        executor.execute(actions);

        if (!actions.isEmpty()) {
            ledger.prune(now);
            persister.accept(StateMapper.snapshot(ledger, stock, tracker, currentConfig));
        }
    }

    /**
     * Inventory restricted to what the flipper bought: per item the lesser of what is in the
     * inventory and what the stock ledger says we own. Pre-owned items are never offered.
     */
    private Map<Integer, Integer> sellableStock() {
        Map<Integer, Integer> sellable = new LinkedHashMap<>();
        Map<Integer, Integer> owned = stock.ownedQuantities();
        for (Map.Entry<Integer, Integer> entry : client.stock().entrySet()) {
            int ownedQty = owned.getOrDefault(entry.getKey(), 0);
            int qty = Math.min(entry.getValue(), ownedQty);
            if (qty > 0) {
                sellable.put(entry.getKey(), qty);
            }
        }
        return sellable;
    }
}
