package com.osrsscripts.accountbuilder

import com.osrsscripts.core.task.Task
import org.tribot.automation.script.ScriptContext

/**
 * Phase 0 placeholder task: logs a heartbeat each tick so the loop can be confirmed running in Echo.
 * Replaced by the real backlog task ([com.osrsscripts.core.task.Task] tiers) in later phases.
 */
internal class HeartbeatTask(private val context: ScriptContext) : Task {

    override fun shouldRun(): Boolean = true

    override fun execute() {
        context.logger.info("AIO Account Builder heartbeat")
    }

    override fun name(): String = "heartbeat"
}
