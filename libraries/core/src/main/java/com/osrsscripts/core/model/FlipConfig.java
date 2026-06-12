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
    private final Duration maxOfferAge;

    private FlipConfig(Builder b) {
        this.capitalCap = b.capitalCap;
        this.perItemCapitalCap = b.perItemCapitalCap;
        this.minMarginGp = b.minMarginGp;
        this.minMarginPct = b.minMarginPct;
        this.minVolume = b.minVolume;
        this.maxSlots = b.maxSlots;
        this.maxOfferAge = b.maxOfferAge;
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

    /** A live offer older than this is considered stale and is cancelled. */
    public Duration maxOfferAge() {
        return maxOfferAge;
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
        private Duration maxOfferAge = Duration.ofMinutes(30);

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

        public Builder maxOfferAge(Duration v) {
            this.maxOfferAge = v;
            return this;
        }

        public FlipConfig build() {
            return new FlipConfig(this);
        }
    }
}
