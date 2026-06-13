package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeHistoryTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(600);

    @Test
    void aggregatesSalesPerItem() {
        TradeHistory history = new TradeHistory();

        history.recordSale(100, 5, 250L, false, T0);
        history.recordSale(100, 5, -50L, true, T1);
        history.recordSale(200, 1, 1_000L, true, T0);

        TradeHistory.ItemRecord first = history.records().get(0);
        assertEquals(200, first.itemId(), "sorted by net profit, best first");
        assertEquals(1_000L, first.netProfit());
        assertEquals(1, first.flipsCompleted());

        TradeHistory.ItemRecord second = history.records().get(1);
        assertEquals(100, second.itemId());
        assertEquals(200L, second.netProfit());
        assertEquals(10, second.qtySold());
        assertEquals(1, second.flipsCompleted(), "only the completing sale counts a flip");
        assertEquals(T1.toEpochMilli(), second.lastTradedEpochMillis());
    }

    @Test
    void avoidsItemsAtOrBelowTheLossThreshold() {
        TradeHistory history = new TradeHistory();
        history.recordSale(100, 5, -1_000L, true, T0);
        history.recordSale(200, 5, -999L, true, T0);
        history.recordSale(300, 5, 50L, true, T0);

        assertTrue(history.shouldAvoid(100, 1_000L));
        assertFalse(history.shouldAvoid(200, 1_000L), "above the threshold: still tradeable");
        assertFalse(history.shouldAvoid(300, 1_000L));
        assertFalse(history.shouldAvoid(999, 1_000L), "never traded: no opinion");
        assertFalse(history.shouldAvoid(100, 0L), "threshold 0 disables avoidance");
    }

    @Test
    void clearForgetsEverything() {
        TradeHistory history = new TradeHistory();
        history.recordSale(100, 5, -5_000L, true, T0);

        history.clear();

        assertTrue(history.records().isEmpty());
        assertFalse(history.shouldAvoid(100, 1_000L), "a cleared loser gets a fresh start");
    }

    @Test
    void recordsRoundTripThroughLoad() {
        TradeHistory history = new TradeHistory();
        history.recordSale(100, 5, 250L, true, T0);
        List<TradeHistory.ItemRecord> snapshot = history.records();

        TradeHistory restored = new TradeHistory();
        restored.load(snapshot);

        assertEquals(1, restored.records().size());
        assertEquals(250L, restored.records().get(0).netProfit());
        restored.recordSale(100, 1, 50L, false, T1);
        assertEquals(300L, restored.records().get(0).netProfit(), "aggregation continues");
    }
}
