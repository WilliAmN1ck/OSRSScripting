package com.osrsscripts.geflipper;

import com.osrsscripts.core.humanize.DelayDistribution;
import java.time.Instant;
import java.util.Objects;

/**
 * Performs a small "fidget" (camera drift, glancing at a side tab) at randomized intervals while
 * the flipper idles, so a watcher sees a player rather than a statue. The schedule comes from a
 * {@link DelayDistribution}; the fidget itself is supplied (SDK-coupled in production).
 */
public final class HumanizedIdle implements IdleBehavior {

    private final DelayDistribution delays;
    private final Runnable fidget;
    private Instant nextFidgetAt;

    public HumanizedIdle(DelayDistribution delays, Runnable fidget) {
        this.delays = Objects.requireNonNull(delays, "delays");
        this.fidget = Objects.requireNonNull(fidget, "fidget");
    }

    @Override
    public void onIdle(Instant now) {
        if (nextFidgetAt == null) {
            // Just went idle: schedule the first fidget rather than reacting instantly.
            nextFidgetAt = now.plusMillis(delays.nextMs());
            return;
        }
        if (!now.isBefore(nextFidgetAt)) {
            fidget.run();
            nextFidgetAt = now.plusMillis(delays.nextMs());
        }
    }
}
