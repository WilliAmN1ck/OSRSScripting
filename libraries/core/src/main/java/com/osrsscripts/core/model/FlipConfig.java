package com.osrsscripts.core.model;

import java.time.Duration;

/**
 * Tunable parameters for a flipping run. Built via {@link #builder()}.
 */
public final class FlipConfig {

    private final long capitalCap;
    private final long perItemCapitalCap;
    private final long minMarginGp;
    private final double minMarginPct;
    private final long minVolume;
    private final int maxSlots;
    private final Duration maxOfferAgeBuy;
    private final Duration maxOfferAgeSell;
    private final boolean membersItemsAllowed;
    private final long minDeploymentGp;
    private final int sellExitAfterRelists;
    private final long avoidAfterLossGp;

    private FlipConfig(Builder b) {
        this.capitalCap = b.capitalCap;
        this.perItemCapitalCap = b.perItemCapitalCap;
        this.minMarginGp = b.minMarginGp;
        this.minMarginPct = b.minMarginPct;
        this.minVolume = b.minVolume;
        this.maxSlots = b.maxSlots;
        this.maxOfferAgeBuy = b.maxOfferAgeBuy;
        this.maxOfferAgeSell = b.maxOfferAgeSell;
        this.membersItemsAllowed = b.membersItemsAllowed;
        this.minDeploymentGp = b.minDeploymentGp;
        this.sellExitAfterRelists = b.sellExitAfterRelists;
        this.avoidAfterLossGp = b.avoidAfterLossGp;
    }

    /**
     * Maximum total gp to deploy across buy offers. Defaults to {@code 0}, which disables buying
     * entirely — the engine places no buy offers until this is set.
     */
    public long capitalCap() {
        return capitalCap;
    }

    /** Maximum gp to deploy on any single item. */
    public long perItemCapitalCap() {
        return perItemCapitalCap;
    }

    /** Minimum acceptable net (post-tax) margin per item, in gp. */
    public long minMarginGp() {
        return minMarginGp;
    }

    /** Minimum acceptable net margin as a fraction of buy price (e.g. 0.02 = 2%). */
    public double minMarginPct() {
        return minMarginPct;
    }

    /** Minimum windowed trade volume for an item to be considered. */
    public long minVolume() {
        return minVolume;
    }

    /** Maximum number of GE slots to use concurrently (1..8). */
    public int maxSlots() {
        return maxSlots;
    }

    /** A live buy offer older than this is considered stale and is cancelled. */
    public Duration maxOfferAgeBuy() {
        return maxOfferAgeBuy;
    }

    /** A live sell offer older than this is considered stale and is cancelled. */
    public Duration maxOfferAgeSell() {
        return maxOfferAgeSell;
    }

    /**
     * Whether members items may be flipped. Disable on a free-to-play account, where the GE
     * rejects offers for members items.
     */
    public boolean membersItemsAllowed() {
        return membersItemsAllowed;
    }

    /**
     * Minimum gp a new buy offer must deploy to be worth a GE slot. Leftover slivers of budget
     * otherwise occupy slots that pending sells need. {@code 0} disables the floor.
     */
    public long minDeploymentGp() {
        return minDeploymentGp;
    }

    /**
     * After this many stale relists of the same item's sell, the next listing goes at the
     * insta-sell (low) price to exit the position. {@code 0} disables escalation.
     */
    public int sellExitAfterRelists() {
        return sellExitAfterRelists;
    }

    /**
     * An item whose recorded net loss reaches this many gp is excluded from buy candidates until
     * the trade history is cleared. {@code 0} disables avoidance.
     */
    public long avoidAfterLossGp() {
        return avoidAfterLossGp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long capitalCap = 0L;
        private long perItemCapitalCap = Long.MAX_VALUE;
        private long minMarginGp = 1L;
        private double minMarginPct = 0.0;
        private long minVolume = 0L;
        private int maxSlots = 8;
        private Duration maxOfferAgeBuy = Duration.ofMinutes(30);
        private Duration maxOfferAgeSell = Duration.ofMinutes(30);
        private boolean membersItemsAllowed = true;
        private long minDeploymentGp = 0L;
        private int sellExitAfterRelists = 0;
        private long avoidAfterLossGp = 0L;

        /** Required for buying: leaving this at the default {@code 0} disables all buy offers. */
        public Builder capitalCap(long v) {
            this.capitalCap = v;
            return this;
        }

        public Builder perItemCapitalCap(long v) {
            this.perItemCapitalCap = v;
            return this;
        }

        public Builder minMarginGp(long v) {
            this.minMarginGp = v;
            return this;
        }

        public Builder minMarginPct(double v) {
            this.minMarginPct = v;
            return this;
        }

        public Builder minVolume(long v) {
            this.minVolume = v;
            return this;
        }

        public Builder maxSlots(int v) {
            this.maxSlots = v;
            return this;
        }

        public Builder maxOfferAgeBuy(Duration v) {
            this.maxOfferAgeBuy = v;
            return this;
        }

        public Builder maxOfferAgeSell(Duration v) {
            this.maxOfferAgeSell = v;
            return this;
        }

        public Builder membersItemsAllowed(boolean v) {
            this.membersItemsAllowed = v;
            return this;
        }

        public Builder minDeploymentGp(long v) {
            this.minDeploymentGp = v;
            return this;
        }

        public Builder sellExitAfterRelists(int v) {
            this.sellExitAfterRelists = v;
            return this;
        }

        public Builder avoidAfterLossGp(long v) {
            this.avoidAfterLossGp = v;
            return this;
        }

        public FlipConfig build() {
            return new FlipConfig(this);
        }
    }
}
