package com.osrsscripts.core.humanize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FidgetSelectorTest {

    @Test
    void neverReturnsTheSameTypeTwiceInARow() {
        FidgetSelector selector = new FidgetSelector(new Random(1));
        FidgetType prev = selector.next();
        for (int i = 0; i < 1000; i++) {
            FidgetType cur = selector.next();
            assertNotEquals(prev, cur, "fidget repeated back-to-back");
            prev = cur;
        }
    }

    @Test
    void everyTypeAppearsOverManyDraws() {
        FidgetSelector selector = new FidgetSelector(new Random(1));
        EnumSet<FidgetType> seen = EnumSet.noneOf(FidgetType.class);
        for (int i = 0; i < 2000; i++) {
            seen.add(selector.next());
        }
        assertEquals(EnumSet.allOf(FidgetType.class), seen);
    }

    @Test
    void zeroWeightTypeIsNeverSelected() {
        Map<FidgetType, Integer> weights = new EnumMap<>(FidgetType.class);
        weights.put(FidgetType.CAMERA, 1);
        weights.put(FidgetType.TAB_GLANCE, 1);
        weights.put(FidgetType.MOUSE_DRIFT, 0);
        FidgetSelector selector = new FidgetSelector(new Random(1), weights);
        for (int i = 0; i < 2000; i++) {
            assertNotEquals(FidgetType.MOUSE_DRIFT, selector.next());
        }
    }

    @Test
    void sameSeedProducesSameSequence() {
        FidgetSelector a = new FidgetSelector(new Random(7));
        FidgetSelector b = new FidgetSelector(new Random(7));
        for (int i = 0; i < 20; i++) {
            assertEquals(a.next(), b.next());
        }
    }
}
