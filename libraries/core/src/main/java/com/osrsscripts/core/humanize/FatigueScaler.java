package com.osrsscripts.core.humanize;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Models session fatigue as a delay multiplier that ramps linearly from {@code 1.0} at the session
 * start to {@code maxMultiplier} after {@code rampOver} has elapsed, then holds — so waits, fidget
 * gaps, and reaction beats all stretch as a session wears on, the way a tiring player slows down.
 */
public final class FatigueScaler {

    private final Instant start;
    private final long rampMs;
    private final double maxMultiplier;

    public FatigueScaler(Instant start, Duration rampOver, double maxMultiplier) {
        this.start = Objects.requireNonNull(start, "start");
        Objects.requireNonNull(rampOver, "rampOver");
        if (rampOver.isZero() || rampOver.isNegative()) {
            throw new IllegalArgumentException("rampOver must be positive");
        }
        if (maxMultiplier < 1.0) {
            throw new IllegalArgumentException("maxMultiplier must be >= 1.0");
        }
        this.rampMs = rampOver.toMillis();
        this.maxMultiplier = maxMultiplier;
    }

    /** The delay multiplier at {@code now}: {@code 1.0} at (or before) the start, rising to the cap. */
    public double multiplierAt(Instant now) {
        long elapsed = Duration.between(start, now).toMillis();
        if (elapsed <= 0) {
            return 1.0;
        }
        double fraction = Math.min(1.0, (double) elapsed / rampMs);
        return 1.0 + fraction * (maxMultiplier - 1.0);
    }
}
