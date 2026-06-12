package com.osrsscripts.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The latest instant-buy / instant-sell prices for an item (OSRS Wiki {@code /latest}).
 *
 * <p>{@code high} is the most recent instant-buy price (what buyers pay) and {@code low} is the
 * most recent instant-sell price (what sellers accept). A flipper buys near {@code low} and sells
 * near {@code high}. A value of {@code 0} means the endpoint reported no recent trade.
 */
public final class PricePoint {

    private final long high;
    private final Instant highTime;
    private final long low;
    private final Instant lowTime;

    public PricePoint(long high, Instant highTime, long low, Instant lowTime) {
        this.high = high;
        this.highTime = highTime;
        this.low = low;
        this.lowTime = lowTime;
    }

    public long high() {
        return high;
    }

    public Instant highTime() {
        return highTime;
    }

    public long low() {
        return low;
    }

    public Instant lowTime() {
        return lowTime;
    }

    public boolean hasHigh() {
        return high > 0;
    }

    public boolean hasLow() {
        return low > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PricePoint)) {
            return false;
        }
        PricePoint other = (PricePoint) o;
        return high == other.high
                && low == other.low
                && Objects.equals(highTime, other.highTime)
                && Objects.equals(lowTime, other.lowTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(high, highTime, low, lowTime);
    }

    @Override
    public String toString() {
        return "PricePoint{high=" + high + ", low=" + low + '}';
    }
}
