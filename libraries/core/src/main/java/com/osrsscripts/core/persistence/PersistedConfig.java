package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Persistable form of the run configuration, so sidebar settings survive a script restart.
 * {@code maxOfferAge} is stored in minutes, matching the sidebar field.
 */
public final class PersistedConfig {

    private final long capitalCap;
    private final long perItemCapitalCap;
    private final long minMarginGp;
    private final double minMarginPct;
    private final long minVolume;
    private final int maxSlots;
    private final long maxOfferAgeMinutes;
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
                           @JsonProperty("maxOfferAgeMinutes") long maxOfferAgeMinutes,
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
        this.maxOfferAgeMinutes = maxOfferAgeMinutes;
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

    public long maxOfferAgeMinutes() {
        return maxOfferAgeMinutes;
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
                && maxOfferAgeMinutes == other.maxOfferAgeMinutes
                && membersItemsAllowed == other.membersItemsAllowed
                && minDeploymentGp == other.minDeploymentGp
                && sellExitAfterRelists == other.sellExitAfterRelists
                && avoidAfterLossGp == other.avoidAfterLossGp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(capitalCap, perItemCapitalCap, minMarginGp, minMarginPct, minVolume,
                maxSlots, maxOfferAgeMinutes, membersItemsAllowed, minDeploymentGp,
                sellExitAfterRelists, avoidAfterLossGp);
    }

    @Override
    public String toString() {
        return "PersistedConfig{capitalCap=" + capitalCap + ", maxSlots=" + maxSlots
                + ", membersItemsAllowed=" + membersItemsAllowed + '}';
    }
}
