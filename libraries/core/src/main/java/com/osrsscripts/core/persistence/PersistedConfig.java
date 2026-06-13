package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Persistable form of the run configuration, so sidebar settings survive a script restart.
 * Offer ages are stored in minutes, matching the sidebar fields.
 */
public final class PersistedConfig {

    private static final long DEFAULT_OFFER_AGE_MINUTES = 30L;

    private final long capitalCap;
    private final long perItemCapitalCap;
    private final long minMarginGp;
    private final double minMarginPct;
    private final long minVolume;
    private final int maxSlots;
    private final long maxOfferAgeBuyMinutes;
    private final long maxOfferAgeSellMinutes;
    private final boolean membersItemsAllowed;
    private final long minDeploymentGp;
    private final int sellExitAfterRelists;
    private final long avoidAfterLossGp;

    @JsonCreator
    public PersistedConfig(@JsonProperty("capitalCap") long capitalCap,
                           @JsonProperty("perItemCapitalCap") long perItemCapitalCap,
                           @JsonProperty("minMarginGp") long minMarginGp,
                           @JsonProperty("minMarginPct") double minMarginPct,
                           @JsonProperty("minVolume") long minVolume,
                           @JsonProperty("maxSlots") int maxSlots,
                           @JsonProperty("maxOfferAgeBuyMinutes") long maxOfferAgeBuyMinutes,
                           @JsonProperty("maxOfferAgeSellMinutes") long maxOfferAgeSellMinutes,
                           @JsonProperty("maxOfferAgeMinutes") Long legacyMaxOfferAgeMinutes,
                           @JsonProperty("membersItemsAllowed") boolean membersItemsAllowed,
                           @JsonProperty("minDeploymentGp") long minDeploymentGp,
                           @JsonProperty("sellExitAfterRelists") int sellExitAfterRelists,
                           @JsonProperty("avoidAfterLossGp") long avoidAfterLossGp) {
        this.capitalCap = capitalCap;
        this.perItemCapitalCap = perItemCapitalCap;
        this.minMarginGp = minMarginGp;
        this.minMarginPct = minMarginPct;
        this.minVolume = minVolume;
        this.maxSlots = maxSlots;
        // Migration: state written before the buy/sell split has only the single
        // maxOfferAgeMinutes; seed both new fields from it (or the default if neither exists),
        // so an upgrading user keeps their setting instead of silently resetting.
        long legacy = legacyMaxOfferAgeMinutes != null
                ? legacyMaxOfferAgeMinutes : DEFAULT_OFFER_AGE_MINUTES;
        this.maxOfferAgeBuyMinutes = maxOfferAgeBuyMinutes > 0 ? maxOfferAgeBuyMinutes : legacy;
        this.maxOfferAgeSellMinutes = maxOfferAgeSellMinutes > 0 ? maxOfferAgeSellMinutes : legacy;
        this.membersItemsAllowed = membersItemsAllowed;
        this.minDeploymentGp = minDeploymentGp;
        this.sellExitAfterRelists = sellExitAfterRelists;
        this.avoidAfterLossGp = avoidAfterLossGp;
    }

    public long capitalCap() {
        return capitalCap;
    }

    public long perItemCapitalCap() {
        return perItemCapitalCap;
    }

    public long minMarginGp() {
        return minMarginGp;
    }

    public double minMarginPct() {
        return minMarginPct;
    }

    public long minVolume() {
        return minVolume;
    }

    public int maxSlots() {
        return maxSlots;
    }

    public long maxOfferAgeBuyMinutes() {
        return maxOfferAgeBuyMinutes;
    }

    public long maxOfferAgeSellMinutes() {
        return maxOfferAgeSellMinutes;
    }

    public boolean membersItemsAllowed() {
        return membersItemsAllowed;
    }

    public long minDeploymentGp() {
        return minDeploymentGp;
    }

    public int sellExitAfterRelists() {
        return sellExitAfterRelists;
    }

    public long avoidAfterLossGp() {
        return avoidAfterLossGp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PersistedConfig)) {
            return false;
        }
        PersistedConfig other = (PersistedConfig) o;
        return capitalCap == other.capitalCap
                && perItemCapitalCap == other.perItemCapitalCap
                && minMarginGp == other.minMarginGp
                && Double.compare(minMarginPct, other.minMarginPct) == 0
                && minVolume == other.minVolume
                && maxSlots == other.maxSlots
                && maxOfferAgeBuyMinutes == other.maxOfferAgeBuyMinutes
                && maxOfferAgeSellMinutes == other.maxOfferAgeSellMinutes
                && membersItemsAllowed == other.membersItemsAllowed
                && minDeploymentGp == other.minDeploymentGp
                && sellExitAfterRelists == other.sellExitAfterRelists
                && avoidAfterLossGp == other.avoidAfterLossGp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(capitalCap, perItemCapitalCap, minMarginGp, minMarginPct, minVolume,
                maxSlots, maxOfferAgeBuyMinutes, maxOfferAgeSellMinutes, membersItemsAllowed,
                minDeploymentGp, sellExitAfterRelists, avoidAfterLossGp);
    }

    @Override
    public String toString() {
        return "PersistedConfig{capitalCap=" + capitalCap + ", maxSlots=" + maxSlots
                + ", membersItemsAllowed=" + membersItemsAllowed + '}';
    }
}
