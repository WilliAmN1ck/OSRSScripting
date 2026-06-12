package com.osrsscripts.core.humanize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class DelayDistributionTest {

    @Test
    void allSamplesAreWithinBounds() {
        DelayDistribution distribution = new DelayDistribution(100, 500, new Random(42));
        for (int i = 0; i < 1000; i++) {
            long ms = distribution.nextMs();
            assertTrue(ms >= 100 && ms <= 500, "out of bounds: " + ms);
        }
    }

    @Test
    void sameSeedProducesSameSequence() {
        DelayDistribution a = new DelayDistribution(100, 500, new Random(7));
        DelayDistribution b = new DelayDistribution(100, 500, new Random(7));
        for (int i = 0; i < 10; i++) {
            assertEquals(a.nextMs(), b.nextMs());
        }
    }

    @Test
    void rejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new DelayDistribution(500, 100, new Random()));
    }
}
