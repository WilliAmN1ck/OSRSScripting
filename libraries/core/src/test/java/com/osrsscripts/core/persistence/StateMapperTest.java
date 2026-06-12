package com.osrsscripts.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.ge.GeTaxRules;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StateMapperTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");

    @Test
    void snapshotAndRestoreRoundTripAllState() {
        BuyLimitLedger buyLimits = new BuyLimitLedger();
        StockLedger stock = new StockLedger();
        OfferTracker tracker = new OfferTracker(buyLimits, stock, new GeTax(GeTaxRules.defaults()));

        // A partially filled buy observed once: populates all three collaborators.
        tracker.observe(List.of(
                new GeOffer(1, OfferStatus.PARTIAL, OfferSide.BUY, 4151, 100L, 10, 4, null)), T0);

        PersistedState state = StateMapper.snapshot(buyLimits, stock, tracker);

        BuyLimitLedger restoredLimits = new BuyLimitLedger();
        StockLedger restoredStock = new StockLedger();
        OfferTracker restoredTracker = new OfferTracker(restoredLimits, restoredStock,
                new GeTax(GeTaxRules.defaults()));
        StateMapper.restore(state, restoredLimits, restoredStock, restoredTracker);

        assertEquals(4, restoredLimits.purchasedWithin(4151, T0));
        assertEquals(4, restoredStock.ownedQty(4151));
        assertEquals(0L, restoredTracker.realizedProfit());

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
                List.of(new OfferStampEntry(1, 4151, "JUNK", 100L, 0, T0.toEpochMilli()),
                        new OfferStampEntry(2, 561, "SELL", 200L, 1, T0.toEpochMilli())),
                0L, 0L);

        OfferTracker tracker = new OfferTracker(new BuyLimitLedger(), new StockLedger(),
                new GeTax(GeTaxRules.defaults()));
        StateMapper.restore(state, new BuyLimitLedger(), new StockLedger(), tracker);

        assertEquals(1, tracker.stamps().size());
        assertEquals(2, tracker.stamps().get(0).slot());
    }
}
