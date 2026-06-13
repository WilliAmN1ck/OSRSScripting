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
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pure flipping decision engine. Given the current market, account state, buy-limit ledger and
 * config, it returns the Grand Exchange actions to perform this tick — without touching any game
 * client. Each tick it, in order: collects finished offers, cancels stale ones, sells owned stock,
 * then spends free slots and capital on the best buy candidates — one offer per item — respecting
 * buy limits and per-item / total capital caps. It also reports why any slots or capital were left
 * idle so the caller can prompt the user to adjust the binding config setting.
 */
public final class FlipEngine {

    public List<FlipAction> decide(List<FlipCandidate> rankedCandidates,
                                   Map<Integer, PricePoint> prices,
                                   AccountState account,
                                   BuyLimitLedger ledger,
                                   FlipConfig config,
                                   Instant now) {
        return decide(rankedCandidates, prices, account, ledger, config, now,
                Collections.emptyMap());
    }

    /**
     * As {@link #decide(List, Map, AccountState, BuyLimitLedger, FlipConfig, Instant)}, with
     * per-item counts of consecutive stale sell relists: once an item's count reaches
     * {@link FlipConfig#sellExitAfterRelists()}, its next listing exits at the insta-sell price.
     */
    public List<FlipAction> decide(List<FlipCandidate> rankedCandidates,
                                   Map<Integer, PricePoint> prices,
                                   AccountState account,
                                   BuyLimitLedger ledger,
                                   FlipConfig config,
                                   Instant now,
                                   Map<Integer, Integer> sellRelistCounts) {
        return plan(rankedCandidates, prices, account, ledger, config, now, sellRelistCounts)
                .actions();
    }

    /** As {@link #plan(List, Map, AccountState, BuyLimitLedger, FlipConfig, Instant, Map)}, with no
     * stale-sell relist history. */
    public FlipPlan plan(List<FlipCandidate> rankedCandidates,
                         Map<Integer, PricePoint> prices,
                         AccountState account,
                         BuyLimitLedger ledger,
                         FlipConfig config,
                         Instant now) {
        return plan(rankedCandidates, prices, account, ledger, config, now, Collections.emptyMap());
    }

    /**
     * The full decision for one tick: the actions to perform plus the reason any GE slots were left
     * idle, so the caller can prompt the user when a config setting — not the market or the
     * account's gold — is what is keeping slots and capital unused.
     */
    public FlipPlan plan(List<FlipCandidate> rankedCandidates,
                         Map<Integer, PricePoint> prices,
                         AccountState account,
                         BuyLimitLedger ledger,
                         FlipConfig config,
                         Instant now,
                         Map<Integer, Integer> sellRelistCounts) {
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
            actions.add(FlipAction.placeSell(slot, itemId, sellPrice(price, itemId, config,
                    sellRelistCounts), qty));
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

        // Pass 3: buy the best candidates within capital, per-item and buy-limit constraints — one
        // offer per item. A single offer already deploys as much as the per-item cap and the 4h buy
        // limit allow, so a second concurrent offer on the same item could buy nothing more; the
        // levers for using idle slots and cash are config, surfaced via the idle reason below.
        long budget = Math.min(account.cash(), Math.max(0L, config.capitalCap() - committedInBuys));
        boolean haveCandidate = false;
        boolean perItemCapBound = false;
        for (FlipCandidate candidate : rankedCandidates) {
            int itemId = candidate.itemId();
            long buyPrice = candidate.buyPrice();
            if (buyPrice <= 0 || busyItems.contains(itemId)
                    || account.stock().containsKey(itemId)) {
                continue;
            }
            haveCandidate = true;
            if (capacity <= 0 || freeSlots.isEmpty() || budget <= 0) {
                continue; // count the candidate, but no room or gold to act on it
            }
            long byBudget = budget / buyPrice;
            long byPerItem = config.perItemCapitalCap() / buyPrice;
            long byLimit = ledger.remaining(itemId, candidate.buyLimit(), now);
            long qty = Math.min(Math.min(byBudget, byPerItem), byLimit);
            if (qty <= 0 || qty * buyPrice < config.minDeploymentGp()) {
                continue; // not worth a slot: leftover slivers block pending sells
            }
            if (byPerItem <= byBudget && byPerItem <= byLimit) {
                perItemCapBound = true; // the per-item cap, not gold or the buy limit, sized this
            }
            int slot = freeSlots.poll();
            actions.add(FlipAction.placeBuy(slot, itemId, buyPrice, (int) qty));
            busyItems.add(itemId);
            budget -= qty * buyPrice;
            capacity--;
        }

        long capHeadroom = Math.max(0L, config.capitalCap() - committedInBuys);
        IdleReason idleReason = idleReason(haveCandidate, freeSlots.size(), capacity, budget,
                capHeadroom <= account.cash(), perItemCapBound);
        return new FlipPlan(actions, idleReason);
    }

    /**
     * Why GE slots or capital were left idle this tick — but only when a config setting is the
     * cause, since that is something the user can change. Being out of gold (all cash deployed) or
     * capped by an item's 4h buy limit is the system working as intended and reports
     * {@link IdleReason#NONE}.
     */
    private static IdleReason idleReason(boolean haveCandidates, int openSlots, int capacity,
                                         long budget, boolean capBinding, boolean perItemCapBound) {
        boolean haveBudget = budget > 0;
        if (capacity <= 0 && openSlots > 0) {
            // maxSlots caps concurrency below the open GE slots — worth flagging only when we had
            // gold and a candidate we could otherwise have deployed into them.
            return haveCandidates && haveBudget ? IdleReason.MAX_SLOTS : IdleReason.NONE;
        }
        if (openSlots > 0) { // free GE slots remain that maxSlots would let us fill
            if (!haveBudget) {
                return capBinding ? IdleReason.CAPITAL_CAP : IdleReason.NONE; // else out of gold
            }
            return IdleReason.NO_CANDIDATES; // gold and slots free, but no more items qualify
        }
        // Every GE slot is working; idle cash can only be deployed with bigger per-item offers.
        return haveBudget && perItemCapBound ? IdleReason.PER_ITEM_CAP : IdleReason.NONE;
    }

    private static boolean isStale(Instant placedAt, Instant now, Duration maxAge) {
        return placedAt != null && Duration.between(placedAt, now).compareTo(maxAge) > 0;
    }

    /** Gp still committed to the unfilled remainder of a live buy offer. */
    private static long remainingCommitment(GeOffer buy) {
        return buy.pricePerItem() * (buy.quantity() - buy.filled());
    }

    /**
     * The price to list a sell at: the insta-buy (high) price normally, dropping to the
     * insta-sell (low) price once the item has been relisted stale too many times — taking the
     * exit beats parking capital forever.
     */
    private static long sellPrice(PricePoint price, int itemId, FlipConfig config,
                                  Map<Integer, Integer> sellRelistCounts) {
        int threshold = config.sellExitAfterRelists();
        if (threshold > 0 && price.hasLow()
                && sellRelistCounts.getOrDefault(itemId, 0) >= threshold) {
            return price.low();
        }
        return price.high();
    }
}
