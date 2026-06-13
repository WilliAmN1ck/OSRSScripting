package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.osrsscripts.core.humanize.DelayDistribution;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HumanizedIdleTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");

    @Test
    void fidgetsOnTheDistributionScheduleNotEveryTick() {
        AtomicInteger fidgets = new AtomicInteger();
        // Degenerate distribution: always exactly 10 seconds.
        HumanizedIdle idle = new HumanizedIdle(
                new DelayDistribution(10_000, 10_000, new Random(1)), fidgets::incrementAndGet);

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
}
