package com.osrsscripts.core.ge;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configurable Grand Exchange sale-tax rules.
 *
 * <p><strong>Verify before trusting profit math.</strong> The live GE tax rate and exemption
 * threshold have changed over the game's history; the {@link #defaults()} values are provisional.
 * The tax engine itself is fully parameterized, so update these without touching logic.
 */
public final class GeTaxRules {

    private final double rate;
    private final long perItemCap;
    private final long exemptBelow;
    private final Set<Integer> exemptItems;

    public GeTaxRules(double rate, long perItemCap, long exemptBelow, Set<Integer> exemptItems) {
        this.rate = rate;
        this.perItemCap = perItemCap;
        this.exemptBelow = exemptBelow;
        this.exemptItems = Collections.unmodifiableSet(new HashSet<>(exemptItems));
    }

    /**
     * Provisional defaults: 2% rate, 5,000,000 gp per-item cap, items at or below 100 gp exempt.
     * <strong>Confirm against the current game before relying on these.</strong>
     */
    public static GeTaxRules defaults() {
        return new GeTaxRules(0.02, 5_000_000L, 100L, Collections.emptySet());
    }

    public double rate() {
        return rate;
    }

    public long perItemCap() {
        return perItemCap;
    }

    public long exemptBelow() {
        return exemptBelow;
    }

    public Set<Integer> exemptItems() {
        return exemptItems;
    }
}
