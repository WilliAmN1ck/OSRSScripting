package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The flipper state that must survive a restart: the buy-limit ledger entries, the stock the
 * flipper bought and has not yet sold, the per-slot offer stamps (placement time + fill
 * baseline), and cumulative profit stats. None of this is recoverable from the game client.
 *
 * <p>Files written before a field existed load with that field empty.
 */
public final class PersistedState {

    private final List<LedgerEntry> ledgerEntries;
    private final List<StockEntry> stockEntries;
    private final List<OfferStampEntry> offerStamps;
    private final long realizedProfit;
    private final long flipsCompleted;

    @JsonCreator
    public PersistedState(@JsonProperty("ledgerEntries") List<LedgerEntry> ledgerEntries,
                          @JsonProperty("stockEntries") List<StockEntry> stockEntries,
                          @JsonProperty("offerStamps") List<OfferStampEntry> offerStamps,
                          @JsonProperty("realizedProfit") long realizedProfit,
                          @JsonProperty("flipsCompleted") long flipsCompleted) {
        this.ledgerEntries = copyOrEmpty(ledgerEntries);
        this.stockEntries = copyOrEmpty(stockEntries);
        this.offerStamps = copyOrEmpty(offerStamps);
        this.realizedProfit = realizedProfit;
        this.flipsCompleted = flipsCompleted;
    }

    private static <T> List<T> copyOrEmpty(List<T> entries) {
        return entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static PersistedState empty() {
        return new PersistedState(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 0L, 0L);
    }

    public List<LedgerEntry> ledgerEntries() {
        return ledgerEntries;
    }

    public List<StockEntry> stockEntries() {
        return stockEntries;
    }

    public List<OfferStampEntry> offerStamps() {
        return offerStamps;
    }

    public long realizedProfit() {
        return realizedProfit;
    }

    public long flipsCompleted() {
        return flipsCompleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PersistedState)) {
            return false;
        }
        PersistedState other = (PersistedState) o;
        return realizedProfit == other.realizedProfit
                && flipsCompleted == other.flipsCompleted
                && ledgerEntries.equals(other.ledgerEntries)
                && stockEntries.equals(other.stockEntries)
                && offerStamps.equals(other.offerStamps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ledgerEntries, stockEntries, offerStamps, realizedProfit,
                flipsCompleted);
    }

    @Override
    public String toString() {
        return "PersistedState{ledger=" + ledgerEntries.size()
                + ", stock=" + stockEntries.size()
                + ", stamps=" + offerStamps.size()
                + ", realizedProfit=" + realizedProfit
                + ", flipsCompleted=" + flipsCompleted + '}';
    }
}
