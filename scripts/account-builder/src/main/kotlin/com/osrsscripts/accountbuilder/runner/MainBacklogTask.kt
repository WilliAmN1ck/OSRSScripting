package com.osrsscripts.accountbuilder.runner

import com.osrsscripts.accountbuilder.engine.BuilderScheduler
import com.osrsscripts.accountbuilder.engine.GameView
import com.osrsscripts.accountbuilder.task.BuilderTask
import com.osrsscripts.core.task.Task

/**
 * Drives the [BuilderScheduler] from the shared [Task] runner: each tick, run one step of the next
 * runnable task. Does nothing when no task is runnable (e.g. every task complete) — the script loop
 * keeps ticking and a higher-priority task (break) can still preempt.
 */
internal class MainBacklogTask(
    private val scheduler: BuilderScheduler,
    private val view: () -> GameView,
) : Task {

    override fun shouldRun(): Boolean = true

    override fun execute() {
        val task = scheduler.next(view()) as? BuilderTask ?: return
        task.execute()
    }

    override fun name(): String = "backlog"
}
