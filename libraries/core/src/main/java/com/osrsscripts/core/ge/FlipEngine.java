package com.osrsscripts.core.ge;

import com.osrsscripts.core.model.AccountState;
import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.PricePoint;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pure flipping decision engine. Given the current market, account state, buy-limit ledger and
 * config, it returns the Grand Exchange actions to perform this tick — without touching any game
 * client. Each tick it, in order: collects finished offers, cancels stale ones, sells owned stock,
 * then spends free slots and capital on the best buy candidates (respecting buy limits and
 * per-item / total capital caps).
 */
public final class FlipEngine {

    public List<FlipAction> decide(List<FlipCandidate> rankedCandidates,
                                   Map<Integer, PricePoint> prices,
                                   AccountState account,
                                   BuyLimitLedger ledger,
                                   FlipConfig config,
                                   Instant now) {
        List<FlipAction> actions = new ArrayList<>();
        Deque<Integer> freeSlots = new ArrayDeque<>();
        Set<Integer> busyItems = new HashSet<>();
        List<GeOffer> liveBuys = new ArrayList<>();
        long committedInBuys = 0L;
        int keptLive = 0;

        // Pass 1: collect finished offers, cancel stale ones, tally commitments and free slots.
        for (GeOffer offer : account.offers()) {
            if (offer.isEmpty()) {
                freeSlots.add(offer.slot());
                continue;
            }
            busyItems.add(offer.itemId());
            if (offer.isCollectable()) {
                actions.add(FlipAction.collect(offer.slot()));
                freeSlots.add(offer.slot());
            } else { // live
                if (isStale(offer.placedAt(), now, config.maxOfferAge())) {
                    actions.add(FlipAction.cancel(offer.slot()));
                    freeSlots.add(offer.slot());
                } else {
                    keptLive++;
                    if (offer.side() == OfferSide.BUY) {
                        committedInBuys += offer.pricePerItem() * offer.quantity();
                        liveBuys.add(offer);
                    }
                }
            }
        }

        int capacity = config.maxSlots() - keptLive;

        // Pass 2: sell owned stock; sells outrank buys for slots, so when none is available the
        // weakest live buy (smallest remaining commitment) is evicted to free one next tick.
        boolean sellBlocked = false;
        for (Map.Entry<Integer, Integer> entry : account.stock().entrySet()) {
            int itemId = entry.getKey();
            int qty = entry.getValue();
            PricePoint price = prices.get(itemId);
            if (qty <= 0 || price == null || !price.hasHigh()) {
                continue;
            }
            if (capacity <= 0 || freeSlots.isEmpty()) {
                sellBlocked = true;
                break;
            }
            int slot = freeSlots.poll();
            actions.add(FlipAction.placeSell(slot, itemId, price.high(), qty));
            busyItems.add(itemId);
            capacity--;
        }
        if (sellBlocked && !liveBuys.isEmpty()) {
            GeOffer weakest = liveBuys.get(0);
            for (GeOffer buy : liveBuys) {
                if (remainingCommitment(buy) < remainingCommitment(weakest)) {
                    weakest = buy;
                }
            }
            actions.add(FlipAction.cancel(weakest.slot()));
        }

        // Pass 3: buy the best candidates within capital, per-item and buy-limit constraints.
        long budget = Math.min(account.cash(), Math.max(0L, config.capitalCap() - committedInBuys));
        for (FlipCandidate candidate : rankedCandidates) {
            if (capacity <= 0 || freeSlots.isEmpty() || budget <= 0) {
                break;
            }
            int itemId = candidate.itemId();
            if (busyItems.contains(itemId) || account.stock().containsKey(itemId)) {
                continue;
            }
            long buyPrice = candidate.buyPrice();
            if (buyPrice <= 0) {
                continue;
            }
            long byBudget = budget / buyPrice;
            long byPerItem = config.perItemCapitalCap() / buyPrice;
            long byLimit = ledger.remaining(itemId, candidate.buyLimit(), now);
            long qty = Math.min(Math.min(byBudget, byPerItem), byLimit);
            if (qty <= 0) {
                continue;
            }
            if (qty * buyPrice < config.minDeploymentGp()) {
                continue; // not worth a slot: leftover slivers block pending sells
            }
            int slot = freeSlots.poll();
            actions.add(FlipAction.placeBuy(slot, itemId, buyPrice, (int) qty));
            busyItems.add(itemId);
            budget -= qty * buyPrice;
            capacity--;
        }

        return actions;
    }

    private static boolean isStale(Instant placedAt, Instant now, Duration maxAge) {
        return placedAt != null && Duration.between(placedAt, now).compareTo(maxAge) > 0;
    }

    /** Gp still committed to the unfilled remainder of a live buy offer. */
    private static long remainingCommitment(GeOffer buy) {
        return buy.pricePerItem() * (buy.quantity() - buy.filled());
    }
}
