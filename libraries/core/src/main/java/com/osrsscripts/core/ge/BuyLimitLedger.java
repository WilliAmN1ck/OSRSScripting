package com.osrsscripts.core.ge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks how much of each item has been purchased within a rolling window (the GE 4-hour buy
 * limit), so the engine never exceeds a limit — including across restarts (the entries are
 * persisted, since this state cannot be recovered from the game client).
 *
 * <p>Queries are side-effect free; call {@link #prune(Instant)} explicitly to discard expired
 * entries (e.g. before persisting).
 */
public final class BuyLimitLedger {

    public static final Duration DEFAULT_WINDOW = Duration.ofHours(4);

    private final Duration window;
    private final List<Purchase> purchases = new ArrayList<>();

    public BuyLimitLedger() {
        this(DEFAULT_WINDOW);
    }

    public BuyLimitLedger(Duration window) {
        this.window = window;
    }

    public void recordPurchase(int itemId, int qty, Instant at) {
        purchases.add(new Purchase(itemId, qty, at));
    }

    /** Quantity of {@code itemId} bought within the window ending at {@code now}. */
    public int purchasedWithin(int itemId, Instant now) {
        Instant cutoff = now.minus(window);
        int total = 0;
        for (Purchase p : purchases) {
            if (p.itemId() == itemId && p.at().isAfter(cutoff)) {
                total += p.qty();
            }
        }
        return total;
    }

    /**
     * Remaining quantity buyable for {@code itemId} given {@code buyLimit}. A non-positive
     * {@code buyLimit} means "no published limit" and returns {@link Integer#MAX_VALUE}.
     */
    public int remaining(int itemId, int buyLimit, Instant now) {
        if (buyLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, buyLimit - purchasedWithin(itemId, now));
    }

    /** Discards entries that fell outside the window ending at {@code now}. */
    public void prune(Instant now) {
        Instant cutoff = now.minus(window);
        purchases.removeIf(p -> !p.at().isAfter(cutoff));
    }

    /** A copy of the current entries, for persistence. */
    public List<Purchase> purchases() {
        return new ArrayList<>(purchases);
    }

    /** Replaces all entries (e.g. when loading persisted state). */
    public void load(List<Purchase> entries) {
        purchases.clear();
        purchases.addAll(entries);
    }

    public static final class Purchase {
        private final int itemId;
        private final int qty;
        private final Instant at;

        public Purchase(int itemId, int qty, Instant at) {
            this.itemId = itemId;
            this.qty = qty;
            this.at = at;
        }

        public int itemId() {
            return itemId;
        }

        public int qty() {
            return qty;
        }

        public Instant at() {
            return at;
        }
    }
}
