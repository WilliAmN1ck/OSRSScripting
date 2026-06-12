package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Persistable form of one offer stamp: the slot's offer identity (item, side, price), the
 * last-seen fill count, and the placement time as epoch millis. {@code side} is the
 * {@code OfferSide} enum name.
 */
public final class OfferStampEntry {

    private final int slot;
    private final int itemId;
    private final String side;
    private final long pricePerItem;
    private final int filled;
    private final long placedAtEpochMillis;

    @JsonCreator
    public OfferStampEntry(@JsonProperty("slot") int slot,
                           @JsonProperty("itemId") int itemId,
                           @JsonProperty("side") String side,
                           @JsonProperty("pricePerItem") long pricePerItem,
                           @JsonProperty("filled") int filled,
                           @JsonProperty("placedAtEpochMillis") long placedAtEpochMillis) {
        this.slot = slot;
        this.itemId = itemId;
        this.side = side;
        this.pricePerItem = pricePerItem;
        this.filled = filled;
        this.placedAtEpochMillis = placedAtEpochMillis;
    }

    public int slot() {
        return slot;
    }

    public int itemId() {
        return itemId;
    }

    public String side() {
        return side;
    }

    public long pricePerItem() {
        return pricePerItem;
    }

    public int filled() {
        return filled;
    }

    public long placedAtEpochMillis() {
        return placedAtEpochMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OfferStampEntry)) {
            return false;
        }
        OfferStampEntry other = (OfferStampEntry) o;
        return slot == other.slot
                && itemId == other.itemId
                && pricePerItem == other.pricePerItem
                && filled == other.filled
                && placedAtEpochMillis == other.placedAtEpochMillis
                && Objects.equals(side, other.side);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, itemId, side, pricePerItem, filled, placedAtEpochMillis);
    }

    @Override
    public String toString() {
        return "OfferStampEntry{slot=" + slot + ", itemId=" + itemId + ", side=" + side
                + ", price=" + pricePerItem + ", filled=" + filled
                + ", placedAt=" + placedAtEpochMillis + '}';
    }
}
