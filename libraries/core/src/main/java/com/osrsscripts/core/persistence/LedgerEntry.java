package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Persistable form of a buy-limit purchase: item, quantity, and epoch-millis timestamp. */
public final class LedgerEntry {

    private final int itemId;
    private final int qty;
    private final long epochMillis;

    @JsonCreator
    public LedgerEntry(@JsonProperty("itemId") int itemId,
                       @JsonProperty("qty") int qty,
                       @JsonProperty("epochMillis") long epochMillis) {
        this.itemId = itemId;
        this.qty = qty;
        this.epochMillis = epochMillis;
    }

    public int itemId() {
        return itemId;
    }

    public int qty() {
        return qty;
    }

    public long epochMillis() {
        return epochMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerEntry)) {
            return false;
        }
        LedgerEntry other = (LedgerEntry) o;
        return itemId == other.itemId && qty == other.qty && epochMillis == other.epochMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, qty, epochMillis);
    }

    @Override
    public String toString() {
        return "LedgerEntry{itemId=" + itemId + ", qty=" + qty + ", epochMillis=" + epochMillis + '}';
    }
}
