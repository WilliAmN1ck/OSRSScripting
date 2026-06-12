package com.osrsscripts.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of the account as it relates to flipping: free cash, the Grand Exchange
 * slots, and items currently owned and available to sell ({@code stock}).
 */
public final class AccountState {

    private final long cash;
    private final List<GeOffer> offers;
    private final Map<Integer, Integer> stock;

    public AccountState(long cash, List<GeOffer> offers, Map<Integer, Integer> stock) {
        this.cash = cash;
        this.offers = Collections.unmodifiableList(new ArrayList<>(offers));
        this.stock = Collections.unmodifiableMap(new LinkedHashMap<>(stock));
    }

    public long cash() {
        return cash;
    }

    public List<GeOffer> offers() {
        return offers;
    }

    /** Item id -> quantity owned and not yet committed to a sell offer. */
    public Map<Integer, Integer> stock() {
        return stock;
    }

    /** Slot numbers of currently free slots. */
    public List<Integer> emptySlots() {
        List<Integer> result = new ArrayList<>();
        for (GeOffer offer : offers) {
            if (offer.isEmpty()) {
                result.add(offer.slot());
            }
        }
        return result;
    }

    /** Number of slots not currently free. */
    public int usedSlots() {
        int used = 0;
        for (GeOffer offer : offers) {
            if (!offer.isEmpty()) {
                used++;
            }
        }
        return used;
    }
}
