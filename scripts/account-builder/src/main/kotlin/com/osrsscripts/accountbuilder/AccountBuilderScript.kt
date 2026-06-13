package com.osrsscripts.accountbuilder

import com.osrsscripts.core.task.TaskRunner
import org.tribot.automation.TribotScript
import org.tribot.automation.script.ScriptContext

/**
 * Entry point for the AIO Account Builder.
 *
 * Phase 0 scaffold: a heartbeat loop on the shared [TaskRunner], proving the Kotlin module loads and
 * runs in Echo before the task engine is built on top of it. The engine, capability seams, and
 * behaviour-tree tasks are added in later phases (see docs/plans/aio-account-builder/plan.md).
 */
class AccountBuilderScript : TribotScript {

    override fun execute(context: ScriptContext) {
        val runner = TaskRunner(listOf(HeartbeatTask(context)))
        while (!Thread.currentThread().isInterrupted) {
            runner.tick()
            context.waiting.sleep(HEARTBEAT_PERIOD_MS)
        }
    }

    private companion object {
        const val HEARTBEAT_PERIOD_MS = 2_000L
    }
}
