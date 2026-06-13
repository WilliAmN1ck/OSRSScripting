package com.osrsscripts.core.humanize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FatigueScalerTest {

    private final Instant start = Instant.parse("2026-06-13T00:00:00Z");

    @Test
    void startsAtOne() {
        FatigueScaler scaler = new FatigueScaler(start, Duration.ofHours(3), 1.6);
        assertEquals(1.0, scaler.multiplierAt(start), 1e-9);
    }

    @Test
    void rampsLinearlyToTheCapThenHolds() {
        FatigueScaler scaler = new FatigueScaler(start, Duration.ofHours(3), 1.6);
        assertEquals(1.3, scaler.multiplierAt(start.plus(Duration.ofMinutes(90))), 1e-9);
        assertEquals(1.6, scaler.multiplierAt(start.plus(Duration.ofHours(3))), 1e-9);
        assertEquals(1.6, scaler.multiplierAt(start.plus(Duration.ofHours(6))), 1e-9, "capped");
    }

    @Test
    void clampsToOneBeforeStart() {
        FatigueScaler scaler = new FatigueScaler(start, Duration.ofHours(3), 1.6);
        assertEquals(1.0, scaler.multiplierAt(start.minus(Duration.ofMinutes(5))), 1e-9);
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new FatigueScaler(start, Duration.ZERO, 1.6));
        assertThrows(IllegalArgumentException.class,
                () -> new FatigueScaler(start, Duration.ofHours(3), 0.9));
    }
}
