package com.osrsscripts.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single Grand Exchange slot and the offer occupying it. {@code side} and {@code placedAt} are
 * {@code null} for an {@link OfferStatus#EMPTY} slot.
 */
public final class GeOffer {

    private final int slot;
    private final OfferStatus status;
    private final OfferSide side;
    private final int itemId;
    private final long pricePerItem;
    private final int quantity;
    private final int filled;
    private final long transferredGold;
    private final Instant placedAt;

    public GeOffer(int slot, OfferStatus status, OfferSide side, int itemId,
                   long pricePerItem, int quantity, int filled, long transferredGold,
                   Instant placedAt) {
        this.slot = slot;
        this.status = Objects.requireNonNull(status, "status");
        this.side = side;
        this.itemId = itemId;
        this.pricePerItem = pricePerItem;
        this.quantity = quantity;
        this.filled = filled;
        this.transferredGold = transferredGold;
        this.placedAt = placedAt;
    }

    /** Convenience for callers without fill-price data: gold is assumed at the listed price. */
    public GeOffer(int slot, OfferStatus status, OfferSide side, int itemId,
                   long pricePerItem, int quantity, int filled, Instant placedAt) {
        this(slot, status, side, itemId, pricePerItem, quantity, filled,
                pricePerItem * filled, placedAt);
    }

    /** An empty slot. */
    public static GeOffer empty(int slot) {
        return new GeOffer(slot, OfferStatus.EMPTY, null, 0, 0L, 0, 0, 0L, null);
    }

    public int slot() {
        return slot;
    }

    public OfferStatus status() {
        return status;
    }

    public OfferSide side() {
        return side;
    }

    public int itemId() {
        return itemId;
    }

    public long pricePerItem() {
        return pricePerItem;
    }

    public int quantity() {
        return quantity;
    }

    public int filled() {
        return filled;
    }

    /**
     * Gold actually moved by fills so far: spent for a buy, received for a sell. The GE can fill
     * at better prices than listed, so this is the accurate money figure, not {@code price × filled}.
     */
    public long transferredGold() {
        return transferredGold;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public boolean isEmpty() {
        return status == OfferStatus.EMPTY;
    }

    /** True when the slot holds a finished offer that should be collected. */
    public boolean isCollectable() {
        return status == OfferStatus.COMPLETE || status == OfferStatus.CANCELLED;
    }

    /** True when the offer is live (placed, not yet finished). */
    public boolean isLive() {
        return status == OfferStatus.ACTIVE || status == OfferStatus.PARTIAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeOffer)) {
            return false;
        }
        GeOffer other = (GeOffer) o;
        return slot == other.slot
                && itemId == other.itemId
                && pricePerItem == other.pricePerItem
                && quantity == other.quantity
                && filled == other.filled
                && transferredGold == other.transferredGold
                && status == other.status
                && side == other.side
                && Objects.equals(placedAt, other.placedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, status, side, itemId, pricePerItem, quantity, filled,
                transferredGold, placedAt);
    }

    @Override
    public String toString() {
        return "GeOffer{slot=" + slot + ", status=" + status + ", side=" + side
                + ", itemId=" + itemId + ", price=" + pricePerItem
                + ", qty=" + quantity + ", filled=" + filled + '}';
    }
}
