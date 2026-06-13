package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.model.MarketStat;
import org.junit.jupiter.api.Test;

class MarketTrendTest {

    private static MarketStat low(long avgLow) {
        return new MarketStat(avgLow + 100, avgLow, 100, 100);
    }

    @Test
    void flagsASharpDropAsAFallingKnife() {
        // 940 is 6% below 1000 (> the 5% default), so it is a falling knife.
        assertTrue(MarketTrend.isFallingKnife(low(940), low(1000), MarketTrend.DEFAULT_DROP));
    }

    @Test
    void ignoresAMildDip() {
        // 960 is only 4% below 1000.
        assertFalse(MarketTrend.isFallingKnife(low(960), low(1000), MarketTrend.DEFAULT_DROP));
    }

    @Test
    void ignoresARisingPrice() {
        assertFalse(MarketTrend.isFallingKnife(low(1100), low(1000), MarketTrend.DEFAULT_DROP));
    }

    @Test
    void needsBothAveragesToCompare() {
        assertFalse(MarketTrend.isFallingKnife(null, low(1000), MarketTrend.DEFAULT_DROP));
        assertFalse(MarketTrend.isFallingKnife(low(900), null, MarketTrend.DEFAULT_DROP));
        assertFalse(MarketTrend.isFallingKnife(new MarketStat(0, 0, 0, 0), low(1000),
                MarketTrend.DEFAULT_DROP));
    }
}
