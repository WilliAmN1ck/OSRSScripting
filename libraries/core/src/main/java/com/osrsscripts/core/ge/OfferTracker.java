package com.osrsscripts.core.ge;

import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Observes the 8 GE slots tick over tick and derives everything the client API does not expose:
 * offer placement times (first-seen, re-matched by slot + item + side + price), buy fills (fed to
 * the {@link BuyLimitLedger} and {@link StockLedger}), and realized profit from sell fills against
 * the consumed cost basis.
 *
 * <p>Money is measured by each offer's <em>transferred gold</em> — what was actually spent or
 * received — so better-price fills are accounted exactly. Transferred gold for sells is taken as
 * the collectable (post-tax) amount, so no further tax is subtracted here.
 *
 * <p>Stamps carry the last-seen fill count and gold so a restart (via {@link #restore}) does not
 * re-record fills that happened before the previous shutdown.
 */
public final class OfferTracker {

    private final BuyLimitLedger buyLimits;
    private final StockLedger stock;
    private final TradeHistory history;
    private final Map<Integer, Stamp> stamps = new HashMap<>();
    private long realizedProfit;
    private long flipsCompleted;

    public OfferTracker(BuyLimitLedger buyLimits, StockLedger stock, TradeHistory history) {
        this.buyLimits = Objects.requireNonNull(buyLimits, "buyLimits");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * Diffs {@code current} against the previous observation, records fill deltas, and returns
     * the offers with {@code placedAt} stamped (empty slots pass through unstamped).
     */
    public List<GeOffer> observe(List<GeOffer> current, Instant now) {
        Set<Integer> seenSlots = new HashSet<>();
        List<GeOffer> stamped = new ArrayList<>(current.size());
        for (GeOffer offer : current) {
            seenSlots.add(offer.slot());
            if (offer.isEmpty()) {
                stamps.remove(offer.slot());
                stamped.add(offer);
                continue;
            }
            Stamp previous = stamps.get(offer.slot());
            Instant placedAt;
            int lastFilled;
            long lastGold;
            if (previous != null && previous.matches(offer)) {
                placedAt = previous.placedAt();
                lastFilled = previous.filled();
                lastGold = previous.transferredGold();
            } else {
                placedAt = now;
                lastFilled = 0;
                lastGold = 0L;
            }
            recordFillDelta(offer, offer.filled() - lastFilled,
                    offer.transferredGold() - lastGold, now);
            stamps.put(offer.slot(), new Stamp(offer.slot(), offer.itemId(), offer.side(),
                    offer.pricePerItem(), offer.filled(), offer.transferredGold(), placedAt));
            stamped.add(new GeOffer(offer.slot(), offer.status(), offer.side(), offer.itemId(),
                    offer.pricePerItem(), offer.quantity(), offer.filled(),
                    offer.transferredGold(), placedAt));
        }
        stamps.keySet().removeIf(slot -> !seenSlots.contains(slot));
        return stamped;
    }

    private void recordFillDelta(GeOffer offer, int delta, long goldDelta, Instant now) {
        if (delta <= 0) {
            return;
        }
        long gold = Math.max(0L, goldDelta);
        if (offer.side() == OfferSide.BUY) {
            buyLimits.recordPurchase(offer.itemId(), delta, now);
            long unitCost = Math.round((double) gold / delta);
            stock.recordBuy(offer.itemId(), delta, unitCost);
        } else if (offer.side() == OfferSide.SELL) {
            long basis = stock.recordSell(offer.itemId(), delta);
            boolean completedFlip = offer.filled() == offer.quantity();
            realizedProfit += gold - basis;
            history.recordSale(offer.itemId(), delta, gold - basis, completedFlip, now);
            if (completedFlip) {
                flipsCompleted++;
            }
        }
    }

    public long realizedProfit() {
        return realizedProfit;
    }

    public long flipsCompleted() {
        return flipsCompleted;
    }

    /** A copy of the current per-slot stamps, for persistence. */
    public List<Stamp> stamps() {
        return new ArrayList<>(stamps.values());
    }

    /**
     * Replaces all tracking state (e.g. when loading persisted state). Restored stamps only
     * re-attach to live offers that still match their identity; anything else is treated as
     * first-seen on the next {@link #observe}.
     */
    public void restore(List<Stamp> entries, long realizedProfit, long flipsCompleted) {
        stamps.clear();
        for (Stamp stamp : entries) {
            stamps.put(stamp.slot(), stamp);
        }
        this.realizedProfit = realizedProfit;
        this.flipsCompleted = flipsCompleted;
    }

    /** The remembered identity, fill/gold baseline, and placement time of one slot's offer. */
    public static final class Stamp {
        private final int slot;
        private final int itemId;
        private final OfferSide side;
        private final long pricePerItem;
        private final int filled;
        private final long transferredGold;
        private final Instant placedAt;

        public Stamp(int slot, int itemId, OfferSide side, long pricePerItem, int filled,
                     long transferredGold, Instant placedAt) {
            this.slot = slot;
            this.itemId = itemId;
            this.side = Objects.requireNonNull(side, "side");
            this.pricePerItem = pricePerItem;
            this.filled = filled;
            this.transferredGold = transferredGold;
            this.placedAt = Objects.requireNonNull(placedAt, "placedAt");
        }

        private boolean matches(GeOffer offer) {
            return itemId == offer.itemId()
                    && side == offer.side()
                    && pricePerItem == offer.pricePerItem();
        }

        public int slot() {
            return slot;
        }

        public int itemId() {
            return itemId;
        }

        public OfferSide side() {
            return side;
        }

        public long pricePerItem() {
            return pricePerItem;
        }

        public int filled() {
            return filled;
        }

        public long transferredGold() {
            return transferredGold;
        }

        public Instant placedAt() {
            return placedAt;
        }
    }
}
