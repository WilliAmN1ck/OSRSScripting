package com.osrsscripts.core.humanize;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Decides when the script should take a break: after each {@code workPeriod} of activity it breaks
 * for {@code breakPeriod}, then resumes. Driven entirely by an injected {@link Clock} so behaviour
 * is deterministic and testable.
 */
public final class BreakScheduler {

    private final Clock clock;
    private final Duration workPeriod;
    private final Duration breakPeriod;
    private Instant nextBreakAt;
    private Instant breakEndsAt;

    public BreakScheduler(Clock clock, Duration workPeriod, Duration breakPeriod) {
        this.clock = clock;
        this.workPeriod = workPeriod;
        this.breakPeriod = breakPeriod;
        this.nextBreakAt = clock.instant().plus(workPeriod);
    }

    /** Whether the script should currently be on break. Advances scheduling state as time passes. */
    public boolean shouldBreak() {
        Instant now = clock.instant();
        if (breakEndsAt != null) {
            if (now.isBefore(breakEndsAt)) {
                return true;
            }
            breakEndsAt = null;
            nextBreakAt = now.plus(workPeriod);
            return false;
        }
        if (!now.isBefore(nextBreakAt)) {
            breakEndsAt = now.plus(breakPeriod);
            return true;
        }
        return false;
    }

    /** When the current break ends, or {@code null} if not on break. */
    public Instant breakEndsAt() {
        return breakEndsAt;
    }
}
