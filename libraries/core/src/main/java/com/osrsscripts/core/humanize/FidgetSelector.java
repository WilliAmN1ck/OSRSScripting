package com.osrsscripts.core.humanize;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Picks the next {@link FidgetType} by weighted random choice, never returning the same type twice
 * in a row (unless it is the only positively-weighted option), so the idle repertoire reads varied
 * rather than cyclic. Driven by an injected {@link Random} for reproducible tests.
 */
public final class FidgetSelector {

    private final Random random;
    private final Map<FidgetType, Integer> weights;
    private FidgetType last;

    public FidgetSelector(Random random) {
        this(random, defaultWeights());
    }

    public FidgetSelector(Random random, Map<FidgetType, Integer> weights) {
        this.random = Objects.requireNonNull(random, "random");
        this.weights = new EnumMap<>(Objects.requireNonNull(weights, "weights"));
        long total = 0;
        for (FidgetType type : FidgetType.values()) {
            int weight = this.weights.getOrDefault(type, 0);
            if (weight < 0) {
                throw new IllegalArgumentException("negative weight for " + type);
            }
            total += weight;
        }
        if (total <= 0) {
            throw new IllegalArgumentException("at least one fidget weight must be positive");
        }
    }

    public FidgetType next() {
        FidgetType chosen = pick();
        last = chosen;
        return chosen;
    }

    private FidgetType pick() {
        // Candidates exclude the previous pick, so the same fidget never fires twice running.
        List<FidgetType> candidates = new ArrayList<>();
        int total = 0;
        for (FidgetType type : FidgetType.values()) {
            int weight = weights.getOrDefault(type, 0);
            if (weight > 0 && type != last) {
                candidates.add(type);
                total += weight;
            }
        }
        if (total == 0) {
            return last; // the previous pick is the only weighted option: allow the repeat
        }
        int roll = random.nextInt(total);
        for (FidgetType type : candidates) {
            roll -= weights.get(type);
            if (roll < 0) {
                return type;
            }
        }
        return candidates.get(candidates.size() - 1); // unreachable; defends against rounding
    }

    private static Map<FidgetType, Integer> defaultWeights() {
        Map<FidgetType, Integer> weights = new EnumMap<>(FidgetType.class);
        weights.put(FidgetType.CAMERA, 4);
        weights.put(FidgetType.TAB_GLANCE, 4);
        weights.put(FidgetType.MOUSE_DRIFT, 3);
        weights.put(FidgetType.HOVER, 3);
        weights.put(FidgetType.WORLD_MAP, 1);
        return weights;
    }
}
