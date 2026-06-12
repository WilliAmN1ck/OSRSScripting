package com.osrsscripts.geflipper;

import com.osrsscripts.core.model.GeOffer;
import java.util.List;
import java.util.Map;

/**
 * The seam between the pure flipping engine and the TRiBot Script SDK. Everything here speaks in
 * {@code libraries:core} types only, so the executor and task logic that depend on it stay
 * unit-testable without a running game client. {@link SdkGeClient} is the sole SDK-backed
 * implementation.
 */
public interface GeClient {

    /** Whether the Grand Exchange interface is currently open. */
    boolean isOpen();

    /** Opens the Grand Exchange interface, returning whether it is open afterwards. */
    boolean open();

    /** Closes the Grand Exchange interface, resetting any half-finished offer-setup state. */
    boolean close();

    /** The eight Grand Exchange slots as offers, slot-indexed, with empty slots as {@link GeOffer#empty}. */
    List<GeOffer> offers();

    /** Free cash (coins) available to spend. */
    long coins();

    /** Item id to quantity owned and available to sell (excludes coins). */
    Map<Integer, Integer> stock();

    /** Places a buy offer; the SDK selects the slot. Returns whether it was placed. */
    boolean placeBuy(int itemId, int price, int quantity);

    /** Places a sell offer; the SDK selects the slot. Returns whether it was placed. */
    boolean placeSell(int itemId, int price, int quantity);

    /** Aborts the offer in the given 1-based slot. */
    boolean abort(int slot);

    /** Collects all finished offers to the inventory. */
    boolean collect();
}
