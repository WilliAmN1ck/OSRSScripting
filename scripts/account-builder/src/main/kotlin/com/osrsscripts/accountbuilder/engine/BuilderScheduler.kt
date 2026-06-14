package com.osrsscripts.accountbuilder.engine

import kotlin.random.Random

/**
 * Picks the next task to run from an ordered backlog: the first task that is not complete and
 * currently validates. Completed tasks are skipped; a task whose requirements are not yet met is
 * passed over until a later tick (e.g. once an earlier training task has raised the level). An
 * optional seed shuffles the backlog once, deterministically, for run-to-run variety.
 */
class BuilderScheduler(tasks: List<TaskSpec>, shuffleSeed: Long? = null) {

    private val backlog: List<TaskSpec> =
        if (shuffleSeed == null) tasks.toList() else tasks.shuffled(Random(shuffleSeed))

    /** The backlog in execution order (after any shuffle). */
    val tasks: List<TaskSpec> get() = backlog

    /** The next runnable task, or null if none is currently runnable. */
    fun next(view: GameView): TaskSpec? =
        backlog.firstOrNull { !it.isComplete(view) && it.validate(view) }

    /**
     * Whether no pending runnable goal remains — every task is either complete or not currently runnable
     * (nothing selected, or requirements that nothing will satisfy). A task that is incomplete AND
     * validates is still pending, so this stays false while real work remains. This lets a run stop
     * cleanly once the configured skills reach their targets, even if another (unconfigured, opt-in)
     * skill never "completes".
     */
    fun allDone(view: GameView): Boolean = backlog.all { it.isComplete(view) || !it.validate(view) }
}
