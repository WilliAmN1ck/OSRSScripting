package com.osrsscripts.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Runs a prioritized list of {@link Task}s. Each {@link #tick()} executes the first task whose
 * {@link Task#shouldRun()} returns true — the standard state-machine pattern for OSRS scripts.
 */
public final class TaskRunner {

    private final List<Task> tasks;

    public TaskRunner(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
    }

    /** Executes the highest-priority eligible task, returning it, or empty if none are eligible. */
    public Optional<Task> tick() {
        for (Task task : tasks) {
            if (task.shouldRun()) {
                task.execute();
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    public List<Task> tasks() {
        return Collections.unmodifiableList(tasks);
    }
}
