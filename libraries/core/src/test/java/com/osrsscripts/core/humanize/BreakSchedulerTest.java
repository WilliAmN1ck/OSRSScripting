package com.osrsscripts.core.humanize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.testutil.AdjustableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BreakSchedulerTest {

    private final Instant start = Instant.parse("2026-06-11T12:00:00Z");

    @Test
    void breaksAfterWorkPeriodThenResumes() {
        AdjustableClock clock = new AdjustableClock(start);
        BreakScheduler scheduler =
                new BreakScheduler(clock, Duration.ofMinutes(10), Duration.ofMinutes(2));

        assertFalse(scheduler.shouldBreak(), "no break at start");

        clock.advance(Duration.ofMinutes(10));
        assertTrue(scheduler.shouldBreak(), "break due after work period");

        clock.advance(Duration.ofMinutes(1));
        assertTrue(scheduler.shouldBreak(), "still on break");

        clock.advance(Duration.ofMinutes(2));
        assertFalse(scheduler.shouldBreak(), "break finished");

        clock.advance(Duration.ofMinutes(9));
        assertFalse(scheduler.shouldBreak(), "not yet time for next break");

        clock.advance(Duration.ofMinutes(1));
        assertTrue(scheduler.shouldBreak(), "next break due");
    }
}
