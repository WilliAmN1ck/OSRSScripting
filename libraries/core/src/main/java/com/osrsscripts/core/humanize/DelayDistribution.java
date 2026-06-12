package com.osrsscripts.core.humanize;

import java.util.Random;

/**
 * Produces randomized, human-like wait durations in milliseconds. Samples a Gaussian centred on the
 * midpoint of {@code [minMs, maxMs]} (with the range spanning ~4 standard deviations) and clamps to
 * the bounds, so waits cluster around the middle rather than being uniform. The injected
 * {@link Random} makes the sequence reproducible in tests.
 */
public final class DelayDistribution {

    private final long minMs;
    private final long maxMs;
    private final double mean;
    private final double stdDev;
    private final Random random;

    public DelayDistribution(long minMs, long maxMs, Random random) {
        if (minMs > maxMs) {
            throw new IllegalArgumentException("minMs (" + minMs + ") > maxMs (" + maxMs + ")");
        }
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.mean = (minMs + maxMs) / 2.0;
        this.stdDev = (maxMs - minMs) / 4.0;
        this.random = random;
    }

    public long nextMs() {
        long value = Math.round(mean + random.nextGaussian() * stdDev);
        if (value < minMs) {
            return minMs;
        }
        if (value > maxMs) {
            return maxMs;
        }
        return value;
    }
}
