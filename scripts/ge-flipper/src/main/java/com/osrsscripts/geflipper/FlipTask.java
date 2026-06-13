package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.ActionType;
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
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.VolumePoint;
import com.osrsscripts.core.persistence.PersistedState;
import com.osrsscripts.core.persistence.StateMapper;
import com.osrsscripts.core.prices.WikiPriceClient;
import com.osrsscripts.core.task.Task;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
 *
 * <p>The GE interface is touched only with cause: offers and inventory are client state and read
 * fine with it closed, so an idle tick leaves the interface alone, a tick with pending actions
 * opens it (acting the tick after), and a failed placement backs off for {@code retryBackoff}
 * rather than flapping the interface on every tick. A transient market-fetch failure skips the
 * tick rather than killing the script.
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
    /** Idle ticks before the GE interface is closed (a human doesn't stare at a static screen). */
    static final int IDLE_TICKS_BEFORE_CLOSE = 5;

    private final Consumer<PersistedState> persister;
    private final Duration retryBackoff;
    private final IdleBehavior idle;
    private final Map<Integer, Integer> sellRelists = new HashMap<>();
    private Instant retryAfter = Instant.MIN;
    private int idleTicks;

    public FlipTask(GeClient client, WikiPriceClient prices, FlipScanner scanner, FlipEngine engine,
                    GeTax tax, BuyLimitLedger ledger, StockLedger stock, OfferTracker tracker,
                    Supplier<FlipConfig> config, FlipActionExecutor executor,
                    Consumer<PersistedState> persister, Duration retryBackoff,
                    IdleBehavior idle) {
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
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff");
        this.idle = Objects.requireNonNull(idle, "idle");
    }

    @Override
    public boolean shouldRun() {
        return true;
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
        for (GeOffer offer : offers) {
            // A completed sell ends the item's losing streak; relist pressure resets.
            if (!offer.isEmpty() && offer.side() == OfferSide.SELL
                    && offer.status() == OfferStatus.COMPLETE) {
                sellRelists.remove(offer.itemId());
            }
        }
        if (now.isBefore(retryAfter)) {
            return; // backing off after a failed placement; keep observing, stop acting
        }
        AccountState account =
                new AccountState(client.coins(), offers, sellableStock(mapping, currentConfig));
        List<FlipCandidate> candidates = scanner.scan(mapping, latest, volumes, currentConfig, tax);
        List<FlipAction> actions =
                engine.decide(candidates, latest, account, ledger, currentConfig, now, sellRelists);
        if (actions.isEmpty()) {
            // Nothing to do (e.g. every slot committed): after a short grace period close the
            // GE — a human doesn't stare at a static interface — and fidget occasionally.
            idleTicks++;
            if (idleTicks >= IDLE_TICKS_BEFORE_CLOSE && client.isOpen()) {
                client.close();
            }
            idle.onIdle(now);
            return;
        }
        idleTicks = 0;
        if (!client.isOpen()) {
            if (!client.open()) {
                retryAfter = now.plus(retryBackoff); // e.g. not at the GE booth: don't spam open
            }
            return; // act next tick, once the interface is up
        }
        if (!executor.execute(actions)) {
            retryAfter = now.plus(retryBackoff);
        }
        countCancelledSells(actions, offers);
        ledger.prune(now);
        persister.accept(StateMapper.snapshot(ledger, stock, tracker, currentConfig));
    }

    /**
     * Each cancelled live sell is one failed listing for its item; enough of them and the engine
     * exits at the insta-sell price. (A failed abort can re-count next tick — that only hastens
     * the exit, which errs in the safe direction.) Counts are in-memory: a restart loses at most
     * the current streaks, which rebuild within a few offer-age cycles.
     */
    private void countCancelledSells(List<FlipAction> actions, List<GeOffer> offers) {
        for (FlipAction action : actions) {
            if (action.type() != ActionType.CANCEL) {
                continue;
            }
            for (GeOffer offer : offers) {
                if (offer.slot() == action.slot() && offer.isLive()
                        && offer.side() == OfferSide.SELL) {
                    sellRelists.merge(offer.itemId(), 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Inventory restricted to what the flipper bought: per item the lesser of what is in the
     * inventory and what the stock ledger says we own. Pre-owned items are never offered, and on
     * an F2P configuration neither are members items (the GE would reject the offer every tick).
     */
    private Map<Integer, Integer> sellableStock(Map<Integer, ItemMeta> mapping,
                                                FlipConfig currentConfig) {
        Map<Integer, Integer> sellable = new LinkedHashMap<>();
        Map<Integer, Integer> owned = stock.ownedQuantities();
        for (Map.Entry<Integer, Integer> entry : client.stock().entrySet()) {
            if (!currentConfig.membersItemsAllowed()) {
                ItemMeta meta = mapping.get(entry.getKey());
                if (meta == null || meta.members()) {
                    continue;
                }
            }
            int ownedQty = owned.getOrDefault(entry.getKey(), 0);
            int qty = Math.min(entry.getValue(), ownedQty);
            if (qty > 0) {
                sellable.put(entry.getKey(), qty);
            }
        }
        return sellable;
    }
}
