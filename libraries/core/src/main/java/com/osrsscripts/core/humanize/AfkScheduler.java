package com.osrsscripts.core.humanize;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Schedules spontaneous "look-away" AFKs — short idle stretches, distinct from client-scheduled
 * breaks, so a watcher sees a person's attention wander. After a randomized gap in
 * {@code [minGap, maxGap]} an AFK of a randomized {@code [minAfk, maxAfk]} duration becomes due;
 * polling it returns that duration once and schedules the next gap. Driven by an injected
 * {@link Random} for reproducible tests.
 */
public final class AfkScheduler {

    private final Random random;
    private final long minGapMs;
    private final long maxGapMs;
    private final long minAfkMs;
    private final long maxAfkMs;
    private Instant nextAfkAt;

    public AfkScheduler(Instant start, Random random, Duration minGap, Duration maxGap,
                        Duration minAfk, Duration maxAfk) {
        Objects.requireNonNull(start, "start");
        this.random = Objects.requireNonNull(random, "random");
        this.minGapMs = positive(minGap, "minGap");
        this.maxGapMs = atLeast(maxGap, minGapMs, "maxGap");
        this.minAfkMs = positive(minAfk, "minAfk");
        this.maxAfkMs = atLeast(maxAfk, minAfkMs, "maxAfk");
        this.nextAfkAt = start.plusMillis(randomBetween(minGapMs, maxGapMs));
    }

    /**
     * If an AFK is due at {@code now}, returns its duration and schedules the next gap; otherwise
     * empty. The loop should idle (no flip actions) for the returned duration.
     */
    public Optional<Duration> pollAt(Instant now) {
        if (now.isBefore(nextAfkAt)) {
            return Optional.empty();
        }
        long afkMs = randomBetween(minAfkMs, maxAfkMs);
        nextAfkAt = now.plusMillis(randomBetween(minGapMs, maxGapMs));
        return Optional.of(Duration.ofMillis(afkMs));
    }

    private long randomBetween(long lo, long hi) {
        return hi > lo ? lo + (long) (random.nextDouble() * (hi - lo)) : lo;
    }

    private static long positive(Duration d, String name) {
        Objects.requireNonNull(d, name);
        if (d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return d.toMillis();
    }

    private static long atLeast(Duration d, long minMs, String name) {
        long ms = positive(d, name);
        if (ms < minMs) {
            throw new IllegalArgumentException(name + " must be >= its minimum");
        }
        return ms;
    }
}
