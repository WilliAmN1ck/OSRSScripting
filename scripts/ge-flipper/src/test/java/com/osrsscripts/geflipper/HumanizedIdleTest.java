package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.osrsscripts.core.humanize.DelayDistribution;
import com.osrsscripts.core.humanize.FatigueScaler;
import com.osrsscripts.core.humanize.FidgetSelector;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HumanizedIdleTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");

    private HumanizedIdle idle(AtomicInteger fidgets, FatigueScaler fatigue) {
        // Degenerate distribution: always exactly 10 seconds (before fatigue scaling).
        return new HumanizedIdle(new DelayDistribution(10_000, 10_000, new Random(1)),
                new FidgetSelector(new Random(1)), fatigue, type -> fidgets.incrementAndGet());
    }

    @Test
    void fidgetsOnTheDistributionScheduleNotEveryTick() {
        AtomicInteger fidgets = new AtomicInteger();
        HumanizedIdle idle = idle(fidgets, new FatigueScaler(T0, Duration.ofHours(1), 1.0));

        idle.onIdle(T0);                      // going idle schedules, no instant fidget
        assertEquals(0, fidgets.get());

        idle.onIdle(T0.plusSeconds(4));       // not due yet
        idle.onIdle(T0.plusSeconds(8));
        assertEquals(0, fidgets.get());

        idle.onIdle(T0.plusSeconds(10));      // due
        assertEquals(1, fidgets.get());

        idle.onIdle(T0.plusSeconds(12));      // rescheduled, not due
        assertEquals(1, fidgets.get());

        idle.onIdle(T0.plusSeconds(20));      // due again
        assertEquals(2, fidgets.get());
    }

    @Test
    void stretchesTheIntervalByFatigue() {
        AtomicInteger fidgets = new AtomicInteger();
        // Session started 5h ago with a 1h ramp to 2.0 → fully fatigued, multiplier 2.0.
        HumanizedIdle idle = idle(fidgets,
                new FatigueScaler(T0.minus(Duration.ofHours(5)), Duration.ofHours(1), 2.0));

        idle.onIdle(T0);                      // schedules first fidget at T0 + 10s*2.0 = T0+20s
        idle.onIdle(T0.plusSeconds(15));      // a plain 10s interval would have fired; fatigue defers it
        assertEquals(0, fidgets.get());

        idle.onIdle(T0.plusSeconds(20));      // due at the stretched interval
        assertEquals(1, fidgets.get());
    }
}
