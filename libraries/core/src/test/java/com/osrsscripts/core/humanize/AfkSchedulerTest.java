package com.osrsscripts.core.humanize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AfkSchedulerTest {

    private final Instant start = Instant.parse("2026-06-13T00:00:00Z");
    private final Duration minGap = Duration.ofMinutes(12);
    private final Duration maxGap = Duration.ofMinutes(24);
    private final Duration minAfk = Duration.ofSeconds(20);
    private final Duration maxAfk = Duration.ofSeconds(90);

    private AfkScheduler scheduler(long seed) {
        return new AfkScheduler(start, new Random(seed), minGap, maxGap, minAfk, maxAfk);
    }

    @Test
    void noAfkBeforeTheFirstGapElapses() {
        AfkScheduler scheduler = scheduler(1);
        assertFalse(scheduler.pollAt(start).isPresent());
        assertFalse(scheduler.pollAt(start.plus(minGap).minusMillis(1)).isPresent(),
                "first AFK cannot fire before the minimum gap");
    }

    @Test
    void firesAfterTheGapWithABoundedDuration() {
        AfkScheduler scheduler = scheduler(1);
        Optional<Duration> afk = scheduler.pollAt(start.plus(maxGap));
        assertTrue(afk.isPresent(), "an AFK is due once the max gap has passed");
        Duration d = afk.get();
        assertTrue(d.compareTo(minAfk) >= 0 && d.compareTo(maxAfk) <= 0, "duration out of bounds: " + d);
    }

    @Test
    void enforcesAGapBetweenConsecutiveAfks() {
        AfkScheduler scheduler = scheduler(1);
        Instant fired = start.plus(maxGap);
        assertTrue(scheduler.pollAt(fired).isPresent());
        // Immediately after, and before another min-gap, nothing fires.
        assertFalse(scheduler.pollAt(fired).isPresent());
        assertFalse(scheduler.pollAt(fired.plus(minGap).minusMillis(1)).isPresent());
        assertTrue(scheduler.pollAt(fired.plus(maxGap)).isPresent(), "next AFK due after the gap");
    }

    @Test
    void sameSeedProducesSameSchedule() {
        AfkScheduler a = scheduler(7);
        AfkScheduler b = scheduler(7);
        Instant t = start.plus(maxGap);
        assertEquals(a.pollAt(t), b.pollAt(t));
    }
}
