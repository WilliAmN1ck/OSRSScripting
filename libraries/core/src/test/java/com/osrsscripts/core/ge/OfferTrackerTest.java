package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfferTrackerTest {

    private static final int ITEM = 4151;
    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);

    private BuyLimitLedger buyLimits;
    private StockLedger stock;
    private OfferTracker tracker;

    @BeforeEach
    void setUp() {
        buyLimits = new BuyLimitLedger();
        stock = new StockLedger();
        // 2% rate, no exemptions in play above 100 gp.
        tracker = new OfferTracker(buyLimits, stock,
                new GeTax(new GeTaxRules(0.02, 5_000_000L, 100L, Collections.emptySet())));
    }

    private static GeOffer offer(int slot, OfferStatus status, OfferSide side,
                                 long price, int qty, int filled) {
        return new GeOffer(slot, status, side, ITEM, price, qty, filled, null);
    }

    @Test
    void firstSeenOfferIsStampedAndKeepsItsStamp() {
        List<GeOffer> first = tracker.observe(
                List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100L, 10, 0)), T0);
        assertEquals(T0, first.get(0).placedAt());

        List<GeOffer> second = tracker.observe(
                List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100L, 10, 0)), T1);
        assertEquals(T0, second.get(0).placedAt());
    }

    @Test
    void changedIdentityInSlotGetsFreshStamp() {
        tracker.observe(List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100L, 10, 0)), T0);

        // Same slot, different price: a different offer.
        List<GeOffer> observed = tracker.observe(
                List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 90L, 10, 0)), T1);
        assertEquals(T1, observed.get(0).placedAt());
    }

    @Test
    void emptySlotPrunesStampAndStaysUnstamped() {
        tracker.observe(List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100L, 10, 0)), T0);
        List<GeOffer> empty = tracker.observe(List.of(GeOffer.empty(1)), T1);
        assertNull(empty.get(0).placedAt());

        // The same identity reappearing later is a new offer, not the old one.
        List<GeOffer> reused = tracker.observe(
                List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100L, 10, 0)), T2);
        assertEquals(T2, reused.get(0).placedAt());
    }

    @Test
    void buyFillDeltasRecordPurchaseAndStockOnce() {
        tracker.observe(List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100L, 10, 0)), T0);
        tracker.observe(List.of(offer(1, OfferStatus.PARTIAL, OfferSide.BUY, 100L, 10, 4)), T1);

        assertEquals(4, buyLimits.purchasedWithin(ITEM, T1));
        assertEquals(4, stock.ownedQty(ITEM));

        // Unchanged state records nothing new.
        tracker.observe(List.of(offer(1, OfferStatus.PARTIAL, OfferSide.BUY, 100L, 10, 4)), T2);
        assertEquals(4, buyLimits.purchasedWithin(ITEM, T2));
        assertEquals(4, stock.ownedQty(ITEM));
    }

    @Test
    void firstObservationOfAFilledBuyCountsTheFill() {
        // An offer can fill between placement and our first look at it.
        tracker.observe(List.of(offer(1, OfferStatus.COMPLETE, OfferSide.BUY, 100L, 10, 10)), T0);

        assertEquals(10, buyLimits.purchasedWithin(ITEM, T0));
        assertEquals(10, stock.ownedQty(ITEM));
    }

    @Test
    void slotReuseDoesNotProducePhantomDeltas() {
        tracker.observe(List.of(offer(1, OfferStatus.COMPLETE, OfferSide.BUY, 100L, 10, 10)), T0);
        tracker.observe(List.of(GeOffer.empty(1)), T1);

        // New offer in the recycled slot: only its own fills count.
        tracker.observe(List.of(offer(1, OfferStatus.PARTIAL, OfferSide.BUY, 200L, 10, 3)), T2);

        assertEquals(13, buyLimits.purchasedWithin(ITEM, T2));
        assertEquals(13, stock.ownedQty(ITEM));
    }

    @Test
    void sellFillAccruesTaxAwareProfitAgainstCostBasis() {
        stock.recordBuy(ITEM, 10, 100L);

        tracker.observe(List.of(offer(1, OfferStatus.ACTIVE, OfferSide.SELL, 150L, 10, 0)), T0);
        tracker.observe(List.of(offer(1, OfferStatus.COMPLETE, OfferSide.SELL, 150L, 10, 10)), T1);

        // 10 * 150 gross - 10 * 3 tax (2% of 150) - 1000 basis = 470.
        assertEquals(470L, tracker.realizedProfit());
        assertEquals(1L, tracker.flipsCompleted());
        assertEquals(0, stock.ownedQty(ITEM));

        // Re-observing the completed offer changes nothing.
        tracker.observe(List.of(offer(1, OfferStatus.COMPLETE, OfferSide.SELL, 150L, 10, 10)), T2);
        assertEquals(470L, tracker.realizedProfit());
        assertEquals(1L, tracker.flipsCompleted());
    }

    @Test
    void partialSellDoesNotCompleteAFlip() {
        stock.recordBuy(ITEM, 10, 100L);

        tracker.observe(List.of(offer(1, OfferStatus.ACTIVE, OfferSide.SELL, 150L, 10, 0)), T0);
        tracker.observe(List.of(offer(1, OfferStatus.PARTIAL, OfferSide.SELL, 150L, 10, 4)), T1);

        // 4 * 150 - 4 * 3 - 400 basis = 188; not yet a completed flip.
        assertEquals(188L, tracker.realizedProfit());
        assertEquals(0L, tracker.flipsCompleted());
    }

    @Test
    void restoreReattachesMatchingStampsWithoutDoubleCounting() {
        tracker.observe(List.of(offer(1, OfferStatus.PARTIAL, OfferSide.BUY, 100L, 10, 4)), T0);
        assertEquals(4, stock.ownedQty(ITEM));
        List<OfferTracker.Stamp> stamps = tracker.stamps();

        // Fresh collaborators, as after a script restart.
        StockLedger newStock = new StockLedger();
        newStock.load(stock.lots());
        OfferTracker restored = new OfferTracker(new BuyLimitLedger(), newStock,
                new GeTax(new GeTaxRules(0.02, 5_000_000L, 100L, Collections.emptySet())));
        restored.restore(stamps, 470L, 1L);

        assertEquals(470L, restored.realizedProfit());
        assertEquals(1L, restored.flipsCompleted());

        // Matching live offer keeps its original placement time and fill baseline.
        List<GeOffer> observed = restored.observe(
                List.of(offer(1, OfferStatus.PARTIAL, OfferSide.BUY, 100L, 10, 4)), T2);
        assertEquals(T0, observed.get(0).placedAt());
        assertEquals(4, newStock.ownedQty(ITEM)); // no double count of the pre-restart fill

        // A non-matching offer in that slot is treated as brand new.
        OfferTracker fresh = new OfferTracker(new BuyLimitLedger(), new StockLedger(),
                new GeTax(GeTaxRules.defaults()));
        fresh.restore(stamps, 0L, 0L);
        List<GeOffer> mismatch = fresh.observe(
                List.of(offer(1, OfferStatus.ACTIVE, OfferSide.BUY, 90L, 10, 0)), T2);
        assertEquals(T2, mismatch.get(0).placedAt());
    }
}
