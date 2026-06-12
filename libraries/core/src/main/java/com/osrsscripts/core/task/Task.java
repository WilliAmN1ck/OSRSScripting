package com.osrsscripts.core.task;

/**
 * A unit of script behaviour. The runner asks each task in priority order whether it
 * {@link #shouldRun()}; the first that says yes is {@link #execute()}d.
 */
public interface Task {

    /** Whether this task is currently applicable and should run. */
    boolean shouldRun();

    /** Perform one step of this task's work. */
    void execute();

    /** Human-readable name, for logging. */
    default String name() {
        return getClass().getSimpleName();
    }
}
