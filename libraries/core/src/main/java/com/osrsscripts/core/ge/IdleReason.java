package com.osrsscripts.core.ge;

/**
 * Why the engine left Grand Exchange slots idle this tick. Used to tell the user when a config
 * setting — not the market or the account's gold — is what is keeping slots and capital unused.
 */
public enum IdleReason {

    /** Every slot the config allows is working, or no slots are open. Nothing to surface. */
    NONE,

    /** Open GE slots are stranded because {@code maxSlots} caps concurrency below them. */
    MAX_SLOTS,

    /** The capital cap is fully committed, so free slots have no gold to use. */
    CAPITAL_CAP,

    /** The per-item capital cap throttles the available candidates below the open slots. */
    PER_ITEM_CAP,

    /** No item passes the scanner filters (margin / volume), so there is nothing to buy. */
    NO_CANDIDATES
}
