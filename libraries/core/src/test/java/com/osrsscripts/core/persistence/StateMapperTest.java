package com.osrsscripts.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.ge.TradeHistory;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StateMapperTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");

    private static FlipConfig config() {
        return FlipConfig.builder()
                .capitalCap(116_000L)
                .perItemCapitalCap(25_000L)
                .minMarginGp(2L)
                .minMarginPct(0.01)
                .minVolume(5_000L)
                .maxSlots(3)
                .maxOfferAge(Duration.ofMinutes(30))
                .membersItemsAllowed(false)
                .minDeploymentGp(1_000L)
                .sellExitAfterRelists(3)
                .avoidAfterLossGp(2_000L)
                .build();
    }

    @Test
    void configRoundTripsThroughSnapshot() {
        BuyLimitLedger buyLimits = new BuyLimitLedger();
        StockLedger stock = new StockLedger();
        TradeHistory history = new TradeHistory();
        OfferTracker tracker = new OfferTracker(buyLimits, stock, history);

        PersistedState state = StateMapper.snapshot(buyLimits, stock, tracker, history, config());
        FlipConfig restored = StateMapper.restoredConfig(state);

        assertEquals(116_000L, restored.capitalCap());
        assertEquals(25_000L, restored.perItemCapitalCap());
        assertEquals(2L, restored.minMarginGp());
        assertEquals(0.01, restored.minMarginPct());
        assertEquals(5_000L, restored.minVolume());
        assertEquals(3, restored.maxSlots());
        assertEquals(Duration.ofMinutes(30), restored.maxOfferAge());
        assertEquals(false, restored.membersItemsAllowed());
        assertEquals(1_000L, restored.minDeploymentGp());
        assertEquals(3, restored.sellExitAfterRelists());
        assertEquals(2_000L, restored.avoidAfterLossGp());
    }

    @Test
    void missingConfigRestoresNull() {
        assertEquals(null, StateMapper.restoredConfig(PersistedState.empty()));
    }

    @Test
    void snapshotAndRestoreRoundTripAllState() {
        BuyLimitLedger buyLimits = new BuyLimitLedger();
        StockLedger stock = new StockLedger();
        TradeHistory history = new TradeHistory();
        OfferTracker tracker = new OfferTracker(buyLimits, stock, history);

        // A partially filled buy observed once, plus a recorded sale: populates everything.
        tracker.observe(List.of(
                new GeOffer(1, OfferStatus.PARTIAL, OfferSide.BUY, 4151, 100L, 10, 4, null)), T0);
        history.recordSale(561, 2, 80L, true, T0);

        PersistedState state = StateMapper.snapshot(buyLimits, stock, tracker, history, config());

        BuyLimitLedger restoredLimits = new BuyLimitLedger();
        StockLedger restoredStock = new StockLedger();
        TradeHistory restoredHistory = new TradeHistory();
        OfferTracker restoredTracker =
                new OfferTracker(restoredLimits, restoredStock, restoredHistory);
        StateMapper.restore(state, restoredLimits, restoredStock, restoredTracker,
                restoredHistory);

        assertEquals(4, restoredLimits.purchasedWithin(4151, T0));
        assertEquals(4, restoredStock.ownedQty(4151));
        assertEquals(0L, restoredTracker.realizedProfit());
        assertEquals(80L, restoredHistory.records().get(0).netProfit());
        assertEquals(561, restoredHistory.records().get(0).itemId());

        // The restored stamp re-attaches: same placement time, no re-recorded fills.
        List<GeOffer> observed = restoredTracker.observe(List.of(
                new GeOffer(1, OfferStatus.PARTIAL, OfferSide.BUY, 4151, 100L, 10, 4, null)),
                T0.plusSeconds(300));
        assertEquals(T0, observed.get(0).placedAt());
        assertEquals(4, restoredStock.ownedQty(4151));
    }

    @Test
    void restoreSkipsStampsWithUnknownSide() {
        PersistedState state = new PersistedState(List.of(), List.of(),
                List.of(new OfferStampEntry(1, 4151, "JUNK", 100L, 0, 0L, T0.toEpochMilli()),
                        new OfferStampEntry(2, 561, "SELL", 200L, 1, 200L, T0.toEpochMilli())),
                List.of(), null, 0L, 0L);

        OfferTracker tracker = new OfferTracker(new BuyLimitLedger(), new StockLedger(),
                new TradeHistory());
        StateMapper.restore(state, new BuyLimitLedger(), new StockLedger(), tracker,
                new TradeHistory());

        assertEquals(1, tracker.stamps().size());
        assertEquals(2, tracker.stamps().get(0).slot());
    }
}
