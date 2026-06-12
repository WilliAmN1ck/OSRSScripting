package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The flipper state that must survive a restart: the buy-limit ledger entries (not recoverable
 * from the game client) plus cumulative profit stats.
 */
public final class PersistedState {

    private final List<LedgerEntry> ledgerEntries;
    private final long realizedProfit;
    private final long flipsCompleted;

    @JsonCreator
    public PersistedState(@JsonProperty("ledgerEntries") List<LedgerEntry> ledgerEntries,
                          @JsonProperty("realizedProfit") long realizedProfit,
                          @JsonProperty("flipsCompleted") long flipsCompleted) {
        this.ledgerEntries = ledgerEntries == null
                ? new ArrayList<>()
                : new ArrayList<>(ledgerEntries);
        this.realizedProfit = realizedProfit;
        this.flipsCompleted = flipsCompleted;
    }

    public static PersistedState empty() {
        return new PersistedState(Collections.emptyList(), 0L, 0L);
    }

    public List<LedgerEntry> ledgerEntries() {
        return ledgerEntries;
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
                && ledgerEntries.equals(other.ledgerEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ledgerEntries, realizedProfit, flipsCompleted);
    }

    @Override
    public String toString() {
        return "PersistedState{entries=" + ledgerEntries.size() + ", realizedProfit="
                + realizedProfit + ", flipsCompleted=" + flipsCompleted + '}';
    }
}
