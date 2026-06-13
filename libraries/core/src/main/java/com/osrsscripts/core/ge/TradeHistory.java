package com.osrsscripts.core.ge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-item record of realized trading results: what each item has earned or lost across its
 * flips. Items whose net loss reaches the configured threshold are avoided as buy candidates
 * until the history is cleared, so the flipper stops re-trying items that keep losing money.
 * Persisted, since the lesson is worthless if a restart forgets it.
 */
public final class TradeHistory {

    private final Map<Integer, ItemRecord> records = new HashMap<>();

    /**
     * Records one sell fill's realized result. {@code completedFlip} marks the fill that finished
     * its offer, which is when a flip is counted.
     */
    public void recordSale(int itemId, int qty, long profit, boolean completedFlip, Instant at) {
        ItemRecord previous = records.get(itemId);
        records.put(itemId, new ItemRecord(itemId,
                (previous == null ? 0L : previous.netProfit()) + profit,
                (previous == null ? 0 : previous.flipsCompleted()) + (completedFlip ? 1 : 0),
                (previous == null ? 0 : previous.qtySold()) + qty,
                at.toEpochMilli()));
    }

    /** Whether the item has lost {@code lossThresholdGp} or more net; {@code 0} disables. */
    public boolean shouldAvoid(int itemId, long lossThresholdGp) {
        if (lossThresholdGp <= 0) {
            return false;
        }
        ItemRecord record = records.get(itemId);
        return record != null && record.netProfit() <= -lossThresholdGp;
    }

    /** A copy of the records, best net profit first, for display and persistence. */
    public List<ItemRecord> records() {
        List<ItemRecord> copy = new ArrayList<>(records.values());
        copy.sort(Comparator.comparingLong(ItemRecord::netProfit).reversed()
                .thenComparingInt(ItemRecord::itemId));
        return copy;
    }

    /** Forgets everything — losers get a fresh start. */
    public void clear() {
        records.clear();
    }

    /** Replaces all records (e.g. when loading persisted state). */
    public void load(List<ItemRecord> entries) {
        records.clear();
        for (ItemRecord record : entries) {
            records.put(record.itemId(), record);
        }
    }

    /** One item's accumulated trading result. */
    public static final class ItemRecord {
        private final int itemId;
        private final long netProfit;
        private final int flipsCompleted;
        private final int qtySold;
        private final long lastTradedEpochMillis;

        public ItemRecord(int itemId, long netProfit, int flipsCompleted, int qtySold,
                          long lastTradedEpochMillis) {
            this.itemId = itemId;
            this.netProfit = netProfit;
            this.flipsCompleted = flipsCompleted;
            this.qtySold = qtySold;
            this.lastTradedEpochMillis = lastTradedEpochMillis;
        }

        public int itemId() {
            return itemId;
        }

        public long netProfit() {
            return netProfit;
        }

        public int flipsCompleted() {
            return flipsCompleted;
        }

        public int qtySold() {
            return qtySold;
        }

        public long lastTradedEpochMillis() {
            return lastTradedEpochMillis;
        }
    }
}
