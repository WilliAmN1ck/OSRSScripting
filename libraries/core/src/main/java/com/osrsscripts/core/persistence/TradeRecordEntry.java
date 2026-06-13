package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Persistable form of one item's accumulated trading result. */
public final class TradeRecordEntry {

    private final int itemId;
    private final long netProfit;
    private final int flipsCompleted;
    private final int qtySold;
    private final long lastTradedEpochMillis;

    @JsonCreator
    public TradeRecordEntry(@JsonProperty("itemId") int itemId,
                            @JsonProperty("netProfit") long netProfit,
                            @JsonProperty("flipsCompleted") int flipsCompleted,
                            @JsonProperty("qtySold") int qtySold,
                            @JsonProperty("lastTradedEpochMillis") long lastTradedEpochMillis) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TradeRecordEntry)) {
            return false;
        }
        TradeRecordEntry other = (TradeRecordEntry) o;
        return itemId == other.itemId
                && netProfit == other.netProfit
                && flipsCompleted == other.flipsCompleted
                && qtySold == other.qtySold
                && lastTradedEpochMillis == other.lastTradedEpochMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, netProfit, flipsCompleted, qtySold, lastTradedEpochMillis);
    }

    @Override
    public String toString() {
        return "TradeRecordEntry{itemId=" + itemId + ", netProfit=" + netProfit
                + ", flips=" + flipsCompleted + ", qtySold=" + qtySold + '}';
    }
}
