package com.osrsscripts.geflipper;

import com.osrsscripts.core.humanize.DelayDistribution;
import com.osrsscripts.core.humanize.FatigueScaler;
import com.osrsscripts.core.humanize.FidgetSelector;
import com.osrsscripts.core.humanize.FidgetType;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Performs a small fidget (chosen by {@link FidgetSelector}) at randomized intervals while the
 * flipper idles, so a watcher sees a player rather than a statue. The inter-fidget delay comes from
 * a {@link DelayDistribution} scaled by a {@link FatigueScaler}, so gaps stretch as the session
 * wears on. The fidget itself is supplied (SDK-coupled in production) and is expected to swallow its
 * own failures.
 */
public final class HumanizedIdle implements IdleBehavior {

    private final DelayDistribution delays;
    private final FidgetSelector selector;
    private final FatigueScaler fatigue;
    private final Consumer<FidgetType> fidget;
    private Instant nextFidgetAt;

    public HumanizedIdle(DelayDistribution delays, FidgetSelector selector, FatigueScaler fatigue,
                         Consumer<FidgetType> fidget) {
        this.delays = Objects.requireNonNull(delays, "delays");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.fatigue = Objects.requireNonNull(fatigue, "fatigue");
        this.fidget = Objects.requireNonNull(fidget, "fidget");
    }

    @Override
    public void onIdle(Instant now) {
        if (nextFidgetAt == null) {
            // Just went idle: schedule the first fidget rather than reacting instantly.
            nextFidgetAt = now.plusMillis(scaledDelay(now));
            return;
        }
        if (!now.isBefore(nextFidgetAt)) {
            fidgetNow();
            nextFidgetAt = now.plusMillis(scaledDelay(now));
        }
    }

    /** Fire a single fidget immediately — used at safe points during active flipping. */
    public void fidgetNow() {
        fidget.accept(selector.next());
    }

    private long scaledDelay(Instant now) {
        return Math.round(delays.nextMs() * fatigue.multiplierAt(now));
    }
}
