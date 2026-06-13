package com.osrsscripts.core.ge;

import com.osrsscripts.core.model.MarketStat;

/**
 * Short-term price-trend signals shared by the buy scanner and the sell path, so both judge a
 * "falling knife" by the same rule.
 */
public final class MarketTrend {

    /** Default drop fraction (5%) that classifies a sharp recent decline. */
    public static final double DEFAULT_DROP = 0.05;

    private MarketTrend() {
    }

    /**
     * Whether the recent (e.g. 5-minute) average sell-side price has fallen more than
     * {@code dropFraction} below the longer (e.g. 1-hour) average — a price crashing in real time.
     * Buying into one means selling lower; holding through one means dumping fast. Needs both
     * averages present to compare; returns {@code false} otherwise.
     */
    public static boolean isFallingKnife(MarketStat recent, MarketStat longer, double dropFraction) {
        if (recent == null || longer == null
                || recent.avgLowPrice() <= 0 || longer.avgLowPrice() <= 0) {
            return false;
        }
        return recent.avgLowPrice() < longer.avgLowPrice() * (1.0 - dropFraction);
    }
}
