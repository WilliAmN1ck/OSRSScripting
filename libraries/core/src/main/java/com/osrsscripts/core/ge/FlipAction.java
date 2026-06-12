package com.osrsscripts.core.ge;

import java.util.Objects;

/**
 * An abstract Grand Exchange command emitted by the {@link FlipEngine}. This is the seam between
 * the pure decision logic and the SDK executor that actually clicks the GE — the executor is the
 * only code that needs to understand how to carry these out.
 */
public final class FlipAction {

    private final ActionType type;
    private final int slot;
    private final int itemId;
    private final long pricePerItem;
    private final int quantity;

    private FlipAction(ActionType type, int slot, int itemId, long pricePerItem, int quantity) {
        this.type = type;
        this.slot = slot;
        this.itemId = itemId;
        this.pricePerItem = pricePerItem;
        this.quantity = quantity;
    }

    public static FlipAction placeBuy(int slot, int itemId, long pricePerItem, int quantity) {
        return new FlipAction(ActionType.PLACE_BUY, slot, itemId, pricePerItem, quantity);
    }

    public static FlipAction placeSell(int slot, int itemId, long pricePerItem, int quantity) {
        return new FlipAction(ActionType.PLACE_SELL, slot, itemId, pricePerItem, quantity);
    }

    public static FlipAction collect(int slot) {
        return new FlipAction(ActionType.COLLECT, slot, 0, 0L, 0);
    }

    public static FlipAction cancel(int slot) {
        return new FlipAction(ActionType.CANCEL, slot, 0, 0L, 0);
    }

    public ActionType type() {
        return type;
    }

    public int slot() {
        return slot;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlipAction)) {
            return false;
        }
        FlipAction other = (FlipAction) o;
        return slot == other.slot
                && itemId == other.itemId
                && pricePerItem == other.pricePerItem
                && quantity == other.quantity
                && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, slot, itemId, pricePerItem, quantity);
    }

    @Override
    public String toString() {
        return "FlipAction{" + type + " slot=" + slot + " itemId=" + itemId
                + " price=" + pricePerItem + " qty=" + quantity + '}';
    }
}
