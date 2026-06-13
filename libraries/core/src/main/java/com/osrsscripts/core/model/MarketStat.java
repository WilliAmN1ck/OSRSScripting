package com.osrsscripts.core.model;

import java.util.Objects;

/**
 * Averaged prices and trade volumes for an item over a window, from the OSRS Wiki {@code /5m} or
 * {@code /1h} endpoints. {@code avgHighPrice} is the mean insta-buy price and {@code avgLowPrice}
 * the mean insta-sell price; a price of {@code 0} means no trades occurred on that side in the
 * window. {@code highPriceVolume} counts insta-buys, {@code lowPriceVolume} insta-sells.
 */
public final class MarketStat {

    private final long avgHighPrice;
    private final long avgLowPrice;
    private final long highPriceVolume;
    private final long lowPriceVolume;

    public MarketStat(long avgHighPrice, long avgLowPrice, long highPriceVolume,
                      long lowPriceVolume) {
        this.avgHighPrice = avgHighPrice;
        this.avgLowPrice = avgLowPrice;
        this.highPriceVolume = highPriceVolume;
        this.lowPriceVolume = lowPriceVolume;
    }

    /** Mean insta-buy (sell-side) price in the window, or {@code 0} if none traded there. */
    public long avgHighPrice() {
        return avgHighPrice;
    }

    /** Mean insta-sell (buy-side) price in the window, or {@code 0} if none traded there. */
    public long avgLowPrice() {
        return avgLowPrice;
    }

    /** Units bought at the high price (insta-buys) in the window. */
    public long highPriceVolume() {
        return highPriceVolume;
    }

    /** Units sold at the low price (insta-sells) in the window. */
    public long lowPriceVolume() {
        return lowPriceVolume;
    }

    /**
     * The throughput a round-trip flip can sustain: the lesser of the two directional volumes,
     * since a flip must fill both a buy (against insta-sellers) and a sell (against insta-buyers).
     */
    public long balancedVolume() {
        return Math.min(highPriceVolume, lowPriceVolume);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MarketStat)) {
            return false;
        }
        MarketStat other = (MarketStat) o;
        return avgHighPrice == other.avgHighPrice && avgLowPrice == other.avgLowPrice
                && highPriceVolume == other.highPriceVolume
                && lowPriceVolume == other.lowPriceVolume;
    }

    @Override
    public int hashCode() {
        return Objects.hash(avgHighPrice, avgLowPrice, highPriceVolume, lowPriceVolume);
    }

    @Override
    public String toString() {
        return "MarketStat{avgHigh=" + avgHighPrice + ", avgLow=" + avgLowPrice
                + ", highVol=" + highPriceVolume + ", lowVol=" + lowPriceVolume + '}';
    }
}
