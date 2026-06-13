package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.model.AccountState;
import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import com.osrsscripts.core.model.PricePoint;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlipEngineTest {

    private final FlipEngine engine = new FlipEngine();
    private final Instant now = Instant.parse("2026-06-11T12:00:00Z");

    private List<GeOffer> emptySlots(int n) {
        List<GeOffer> offers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            offers.add(GeOffer.empty(i));
        }
        return offers;
    }

    private FlipConfig config() {
        return FlipConfig.builder()
                .capitalCap(10_000_000L)
                .perItemCapitalCap(Long.MAX_VALUE)
                .maxSlots(8)
                .maxOfferAge(Duration.ofMinutes(30))
                .minMarginGp(1)
                .build();
    }

    private FlipCandidate candidate(int itemId, long buy, long sell, int buyLimit) {
        long margin = sell - buy;
        return new FlipCandidate(itemId, buy, sell, margin, 10_000, buyLimit, (double) margin / buy);
    }

    @Test
    void collectsCompletedOffers() {
        List<GeOffer> offers = emptySlots(8);
        offers.set(0, new GeOffer(0, OfferStatus.COMPLETE, OfferSide.BUY, 100, 1000, 50, 50, now));
        AccountState account = new AccountState(1_000_000L, offers, Collections.emptyMap());

        List<FlipAction> actions = engine.decide(Collections.emptyList(), Collections.emptyMap(),
                account, new BuyLimitLedger(), config(), now);

        assertEquals(Collections.singletonList(FlipAction.collect(0)), actions);
    }

    @Test
    void sellsOwnedStock() {
        Map<Integer, PricePoint> prices = new HashMap<>();
        prices.put(100, new PricePoint(1100, now, 1000, now));
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        stock.put(100, 50);
        AccountState account = new AccountState(0L, emptySlots(8), stock);

        List<FlipAction> actions = engine.decide(Collections.emptyList(), prices, account,
                new BuyLimitLedger(), config(), now);

        assertEquals(Collections.singletonList(FlipAction.placeSell(0, 100, 1100, 50)), actions);
    }

    @Test
    void buysTopCandidateRespectingPerItemCap() {
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(100_000L).maxSlots(8).minMarginGp(1)
                .build();
        AccountState account = new AccountState(10_000_000L, emptySlots(8), Collections.emptyMap());
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 1000, 1100, 1000));

        List<FlipAction> actions = engine.decide(ranked, Collections.emptyMap(), account,
                new BuyLimitLedger(), config, now);

        assertEquals(Collections.singletonList(FlipAction.placeBuy(0, 100, 1000, 100)), actions);
    }

    @Test
    void doesNotBuyWhenAllSlotsActive() {
        List<GeOffer> offers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            offers.add(new GeOffer(i, OfferStatus.ACTIVE, OfferSide.BUY, 100 + i, 1000, 10, 0, now));
        }
        AccountState account = new AccountState(10_000_000L, offers, Collections.emptyMap());
        List<FlipCandidate> ranked = Collections.singletonList(candidate(999, 1000, 1100, 1000));

        List<FlipAction> actions = engine.decide(ranked, Collections.emptyMap(), account,
                new BuyLimitLedger(), config(), now);

        assertTrue(actions.isEmpty());
    }

    @Test
    void skipsItemWithExhaustedBuyLimit() {
        AccountState account = new AccountState(10_000_000L, emptySlots(8), Collections.emptyMap());
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.recordPurchase(100, 100, now);
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 1000, 1100, 100));

        List<FlipAction> actions = engine.decide(ranked, Collections.emptyMap(), account, ledger,
                config(), now);

        assertTrue(actions.isEmpty());
    }

    @Test
    void cancelsStaleOffer() {
        List<GeOffer> offers = emptySlots(8);
        Instant old = now.minus(Duration.ofMinutes(31));
        offers.set(0, new GeOffer(0, OfferStatus.ACTIVE, OfferSide.BUY, 100, 1000, 0, 0, old));
        AccountState account = new AccountState(10_000_000L, offers, Collections.emptyMap());

        List<FlipAction> actions = engine.decide(Collections.emptyList(), Collections.emptyMap(),
                account, new BuyLimitLedger(), config(), now);

        assertEquals(Collections.singletonList(FlipAction.cancel(0)), actions);
    }

    @Test
    void minDeploymentFloorSkipsTrivialBuys() {
        // 64 gp of leftover budget must not waste a slot on a 44 gp buy.
        AccountState account = new AccountState(64L, emptySlots(8), Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(8)
                .minMarginGp(1).minDeploymentGp(1_000L)
                .build();
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 22, 30, 1000));

        List<FlipAction> actions = engine.decide(ranked, Collections.emptyMap(), account,
                new BuyLimitLedger(), config, now);

        assertTrue(actions.isEmpty());
    }

    @Test
    void deploymentAtOrAboveFloorStillBuys() {
        AccountState account = new AccountState(50_000L, emptySlots(8), Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(8)
                .minMarginGp(1).minDeploymentGp(1_000L)
                .build();
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 22, 30, 100_000));

        List<FlipAction> actions = engine.decide(ranked, Collections.emptyMap(), account,
                new BuyLimitLedger(), config, now);

        // The whole budget deploys into one offer: 50_000 / 22 = 2272 units, clearing the floor.
        assertEquals(Collections.singletonList(FlipAction.placeBuy(0, 100, 22, 2272)), actions);
    }

    @Test
    void neverBuysAnItemItHoldsOrIsSelling() {
        // Holding the item as stock: no buy, even with slots and budget free.
        Map<Integer, PricePoint> prices = new HashMap<>();
        prices.put(100, new PricePoint(15, now, 10, now));
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        stock.put(100, 5);
        AccountState holding = new AccountState(10_000L, emptySlots(8), stock);
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 10, 15, 10_000));

        List<FlipAction> actions = engine.decide(ranked, prices, holding,
                new BuyLimitLedger(), config(), now);
        assertTrue(actions.stream().noneMatch(a -> a.type() == ActionType.PLACE_BUY),
                "buying what we hold risks matching our own sell");

        // A live sell for the item: same rule.
        List<GeOffer> offers = emptySlots(8);
        offers.set(0, new GeOffer(0, OfferStatus.ACTIVE, OfferSide.SELL, 100, 15, 5, 0, now));
        AccountState selling = new AccountState(10_000L, offers, Collections.emptyMap());
        List<FlipAction> sellingActions = engine.decide(ranked, Collections.emptyMap(), selling,
                new BuyLimitLedger(), config(), now);
        assertTrue(sellingActions.isEmpty());
    }

    @Test
    void pendingSellPreemptsTheWeakestLiveBuy() {
        List<GeOffer> offers = emptySlots(8);
        offers.set(0, new GeOffer(0, OfferStatus.PARTIAL, OfferSide.BUY, 1700, 2030, 57, 3, now));
        offers.set(1, new GeOffer(1, OfferStatus.ACTIVE, OfferSide.BUY, 1519, 22, 2, 0, now));
        offers.set(2, new GeOffer(2, OfferStatus.ACTIVE, OfferSide.BUY, 2357, 100, 1, 0, now));
        Map<Integer, PricePoint> prices = new HashMap<>();
        prices.put(349, new PricePoint(124, now, 110, now));
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        stock.put(349, 2); // waiting to sell, but maxSlots 3 are all buys
        AccountState account = new AccountState(0L, offers, stock);
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(3)
                .minMarginGp(1)
                .build();

        List<FlipAction> actions = engine.decide(Collections.emptyList(), prices, account,
                new BuyLimitLedger(), config, now);

        // Slot 1 holds the smallest remaining commitment (2 x 22 = 44 gp): evict it.
        assertEquals(Collections.singletonList(FlipAction.cancel(1)), actions);
    }

    @Test
    void pendingSellNeverPreemptsLiveSells() {
        List<GeOffer> offers = emptySlots(8);
        for (int i = 0; i < 3; i++) {
            offers.set(i, new GeOffer(i, OfferStatus.ACTIVE, OfferSide.SELL, 200 + i, 100, 5, 0,
                    now));
        }
        Map<Integer, PricePoint> prices = new HashMap<>();
        prices.put(349, new PricePoint(124, now, 110, now));
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        stock.put(349, 2);
        AccountState account = new AccountState(0L, offers, stock);
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(3)
                .minMarginGp(1)
                .build();

        List<FlipAction> actions = engine.decide(Collections.emptyList(), prices, account,
                new BuyLimitLedger(), config, now);

        assertTrue(actions.isEmpty());
    }

    @Test
    void repeatedlyStaleSellExitsAtTheInstaSellPrice() {
        Map<Integer, PricePoint> prices = new HashMap<>();
        prices.put(100, new PricePoint(1100, now, 1000, now)); // high 1100, low 1000
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        stock.put(100, 50);
        AccountState account = new AccountState(0L, emptySlots(8), stock);
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(8)
                .minMarginGp(1).sellExitAfterRelists(3)
                .build();

        // Two failed listings so far: still patient, list at the insta-buy (high) price.
        List<FlipAction> patient = engine.decide(Collections.emptyList(), prices, account,
                new BuyLimitLedger(), config, now, Map.of(100, 2));
        assertEquals(Collections.singletonList(FlipAction.placeSell(0, 100, 1100, 50)), patient);

        // Third relist: exit at the insta-sell (low) price.
        List<FlipAction> exiting = engine.decide(Collections.emptyList(), prices, account,
                new BuyLimitLedger(), config, now, Map.of(100, 3));
        assertEquals(Collections.singletonList(FlipAction.placeSell(0, 100, 1000, 50)), exiting);
    }

    @Test
    void sellExitDisabledKeepsListingAtMarket() {
        Map<Integer, PricePoint> prices = new HashMap<>();
        prices.put(100, new PricePoint(1100, now, 1000, now));
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        stock.put(100, 50);
        AccountState account = new AccountState(0L, emptySlots(8), stock);

        // Default config: sellExitAfterRelists 0 = disabled, even with a huge relist count.
        List<FlipAction> actions = engine.decide(Collections.emptyList(), prices, account,
                new BuyLimitLedger(), config(), now, Map.of(100, 99));

        assertEquals(Collections.singletonList(FlipAction.placeSell(0, 100, 1100, 50)), actions);
    }

    @Test
    void planReportsMaxSlotsWhenConcurrencyCapStrandsOpenSlots() {
        // Three live buys fill maxSlots; five GE slots sit open with gold and a candidate to spare.
        List<GeOffer> offers = emptySlots(8);
        for (int i = 0; i < 3; i++) {
            offers.set(i, new GeOffer(i, OfferStatus.ACTIVE, OfferSide.BUY, 500 + i, 100, 1, 0, now));
        }
        AccountState account = new AccountState(1_000_000L, offers, Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(3).minMarginGp(1)
                .build();
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 1000, 1100, 1000));

        FlipPlan plan = engine.plan(ranked, Collections.emptyMap(), account, new BuyLimitLedger(),
                config, now);

        assertEquals(IdleReason.MAX_SLOTS, plan.idleReason());
    }

    @Test
    void planReportsNoCandidatesWhenFiltersStarveTheEngine() {
        AccountState account = new AccountState(1_000_000L, emptySlots(8), Collections.emptyMap());

        FlipPlan plan = engine.plan(Collections.emptyList(), Collections.emptyMap(), account,
                new BuyLimitLedger(), config(), now);

        assertEquals(IdleReason.NO_CANDIDATES, plan.idleReason());
    }

    @Test
    void planReportsCapitalCapWhenItIsFullyCommitted() {
        // A single live buy commits the whole cap; free slots remain but no budget to use them.
        List<GeOffer> offers = emptySlots(8);
        offers.set(0, new GeOffer(0, OfferStatus.ACTIVE, OfferSide.BUY, 200, 3000, 200, 0, now));
        AccountState account = new AccountState(1_000_000L, offers, Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(600_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(8).minMarginGp(1)
                .build();
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 1000, 1100, 1000));

        FlipPlan plan = engine.plan(ranked, Collections.emptyMap(), account, new BuyLimitLedger(),
                config, now);

        assertEquals(IdleReason.CAPITAL_CAP, plan.idleReason());
    }

    @Test
    void planReportsPerItemCapWhenSlotsAreFullButCashSitsIdle() {
        // The one open slot fills, but the per-item cap sizes the offer to 5 units (5_000 gp),
        // leaving most of the budget undeployed with no slot left to use it.
        AccountState account = new AccountState(1_000_000L, emptySlots(1), Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(5_000L).maxSlots(1).minMarginGp(1)
                .build();
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 1000, 1100, 10_000));

        FlipPlan plan = engine.plan(ranked, Collections.emptyMap(), account, new BuyLimitLedger(),
                config, now);

        assertEquals(IdleReason.PER_ITEM_CAP, plan.idleReason());
    }

    @Test
    void planReportsNoneWhenAllCashIsDeployed() {
        AccountState account = new AccountState(8_000L, emptySlots(4), Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(10_000_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(4).minMarginGp(1)
                .build();
        List<FlipCandidate> ranked = List.of(candidate(100, 10, 15, 10_000));

        FlipPlan plan = engine.plan(ranked, Collections.emptyMap(), account, new BuyLimitLedger(),
                config, now);

        // The whole budget goes into one offer; remaining idle slots are out-of-gold, not config.
        assertEquals(IdleReason.NONE, plan.idleReason());
        assertEquals(Collections.singletonList(FlipAction.placeBuy(0, 100, 10, 800)),
                plan.actions());
    }

    @Test
    void totalCapitalCapLimitsNewBuyQuantity() {
        List<GeOffer> offers = emptySlots(8);
        offers.set(0, new GeOffer(0, OfferStatus.ACTIVE, OfferSide.BUY, 200, 1000, 500, 0, now));
        AccountState account = new AccountState(1_000_000L, offers, Collections.emptyMap());
        FlipConfig config = FlipConfig.builder()
                .capitalCap(600_000L).perItemCapitalCap(Long.MAX_VALUE).maxSlots(8).minMarginGp(1)
                .build();
        List<FlipCandidate> ranked = Collections.singletonList(candidate(100, 1000, 1100, 1000));

        List<FlipAction> actions = engine.decide(ranked, Collections.emptyMap(), account,
                new BuyLimitLedger(), config, now);

        // 500k already committed of the 600k cap: exactly 100 more units at 1000 gp, in one offer.
        assertEquals(Collections.singletonList(FlipAction.placeBuy(1, 100, 1000, 100)), actions);
    }
}
