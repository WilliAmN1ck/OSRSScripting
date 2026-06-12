package com.osrsscripts.core.model;

/** Lifecycle state of a Grand Exchange slot. */
public enum OfferStatus {
    /** Slot is free. */
    EMPTY,
    /** Offer is live with no fills yet. */
    ACTIVE,
    /** Offer is partially filled. */
    PARTIAL,
    /** Offer is fully filled and ready to collect. */
    COMPLETE,
    /** Offer was cancelled (collectable). */
    CANCELLED
}
