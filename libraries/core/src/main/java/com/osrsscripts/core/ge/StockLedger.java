package com.osrsscripts.core.ge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks what the flipper itself bought and has not yet sold, as FIFO lots carrying their cost
 * basis. The sell pass only offers quantities recorded here, so pre-owned inventory is never
 * touched, and realized profit can be computed against the consumed cost basis. Persisted, since
 * this state cannot be recovered from the game client.
 */
public final class StockLedger {

    private final List<Lot> lots = new ArrayList<>();

    public void recordBuy(int itemId, int qty, long pricePerItem) {
        lots.add(new Lot(itemId, qty, pricePerItem));
    }

    /**
     * Removes up to {@code qty} of {@code itemId}, oldest lots first, and returns the cost basis
     * consumed. Quantities beyond what is owned are ignored (their basis is zero).
     */
    public long recordSell(int itemId, int qty) {
        long basis = 0L;
        int remaining = qty;
        for (int i = 0; i < lots.size() && remaining > 0; i++) {
            Lot lot = lots.get(i);
            if (lot.itemId() != itemId) {
                continue;
            }
            int consumed = Math.min(lot.qty(), remaining);
            basis += (long) consumed * lot.pricePerItem();
            remaining -= consumed;
            lots.set(i, new Lot(itemId, lot.qty() - consumed, lot.pricePerItem()));
        }
        lots.removeIf(lot -> lot.qty() == 0);
        return basis;
    }

    public int ownedQty(int itemId) {
        int total = 0;
        for (Lot lot : lots) {
            if (lot.itemId() == itemId) {
                total += lot.qty();
            }
        }
        return total;
    }

    /** Owned quantity per item, for filtering the sellable stock. */
    public Map<Integer, Integer> ownedQuantities() {
        Map<Integer, Integer> owned = new LinkedHashMap<>();
        for (Lot lot : lots) {
            owned.merge(lot.itemId(), lot.qty(), Integer::sum);
        }
        return owned;
    }

    /** A copy of the current lots in FIFO order, for persistence. */
    public List<Lot> lots() {
        return new ArrayList<>(lots);
    }

    /** Replaces all lots (e.g. when loading persisted state). */
    public void load(List<Lot> entries) {
        lots.clear();
        lots.addAll(entries);
    }

    public static final class Lot {
        private final int itemId;
        private final int qty;
        private final long pricePerItem;

        public Lot(int itemId, int qty, long pricePerItem) {
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
    }
}
