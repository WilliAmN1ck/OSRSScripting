package com.osrsscripts.accountbuilder.runner

import com.osrsscripts.accountbuilder.engine.BuilderScheduler
import com.osrsscripts.accountbuilder.engine.GameView
import com.osrsscripts.accountbuilder.task.BuilderTask
import com.osrsscripts.core.task.Task
import org.tribot.script.sdk.Log

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

    private var lastIdleLogMs = 0L

    override fun execute() {
        val next = scheduler.next(view())
        if (next == null) {
            // Not complete (the script checks that first) but nothing is currently runnable — e.g.
            // no tree selected or a task's requirements aren't met. Surface it periodically.
            logIdlePeriodically()
            return
        }
        val task = next as? BuilderTask
        if (task == null) {
            // Should never happen — only BuilderTasks belong in the backlog. If it does, the
            // scheduler would keep returning it and stall, so surface it loudly instead.
            Log.warn("Backlog task '${next.key.value}' is not a BuilderTask — cannot execute; skipping.")
            return
        }
        task.execute()
    }

    private fun logIdlePeriodically() {
        val now = System.currentTimeMillis()
        if (now - lastIdleLogMs >= IDLE_LOG_INTERVAL_MS) {
            lastIdleLogMs = now
            Log.info("Waiting — no task is currently runnable (check axe, tree selection, requirements).")
        }
    }

    override fun name(): String = "backlog"

    private companion object {
        const val IDLE_LOG_INTERVAL_MS = 30_000L
    }
}
