package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Persistable form of one stock-ledger lot: item, quantity, and unit cost. */
public final class StockEntry {

    private final int itemId;
    private final int qty;
    private final long pricePerItem;

    @JsonCreator
    public StockEntry(@JsonProperty("itemId") int itemId,
                      @JsonProperty("qty") int qty,
                      @JsonProperty("pricePerItem") long pricePerItem) {
        this.itemId = itemId;
        this.qty = qty;
        this.pricePerItem = pricePerItem;
    }

    public int itemId() {
        return itemId;
    }

    public int qty() {
        return qty;
    }

    public long pricePerItem() {
        return pricePerItem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StockEntry)) {
            return false;
        }
        StockEntry other = (StockEntry) o;
        return itemId == other.itemId && qty == other.qty && pricePerItem == other.pricePerItem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, qty, pricePerItem);
    }

    @Override
    public String toString() {
        return "StockEntry{itemId=" + itemId + ", qty=" + qty
                + ", pricePerItem=" + pricePerItem + '}';
    }
}
