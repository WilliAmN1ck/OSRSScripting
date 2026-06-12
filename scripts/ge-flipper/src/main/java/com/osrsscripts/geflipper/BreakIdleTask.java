package com.osrsscripts.geflipper;

import com.osrsscripts.core.task.Task;
import java.util.Objects;

/**
 * Idles while the client's break handler has the script on a break. Registered ahead of the other
 * tasks so it shadows them: open offers keep filling passively during the break and are collected
 * once flipping resumes.
 */
public final class BreakIdleTask implements Task {

    private final BreakSource breaks;

    public BreakIdleTask(BreakSource breaks) {
        this.breaks = Objects.requireNonNull(breaks, "breaks");
    }

    @Override
    public boolean shouldRun() {
        return breaks.isOnBreak();
    }

    @Override
    public void execute() {
        // Nothing to do: being selected is what blocks the flipping tasks this tick.
    }

    @Override
    public String name() {
        return "break-idle";
    }
}
