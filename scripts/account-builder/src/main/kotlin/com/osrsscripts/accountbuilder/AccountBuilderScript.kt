package com.osrsscripts.accountbuilder

import com.osrsscripts.core.task.TaskRunner
import org.tribot.automation.TribotScript
import org.tribot.automation.script.ScriptContext
import org.tribot.script.sdk.antiban.Antiban

/**
 * Entry point for the AIO Account Builder.
 *
 * Phase 1 (vertical-slice proof): a single hardcoded chop → bank loop on the shared [TaskRunner],
 * exercising the SDK integration (Query, banking, walking, antiban, break shadowing) end-to-end
 * before the task engine is built. The engine, capability seams, and real tasks come in later phases
 * (see docs/plans/aio-account-builder/plan.md).
 */
class AccountBuilderScript : TribotScript {

    override fun execute(context: ScriptContext) {
        // Echo's built-in action humanization.
        Antiban.setScriptAiAntibanEnabled(true)

        val runner = TaskRunner(listOf(ChopAndBankTask()))
        while (!Thread.currentThread().isInterrupted) {
            // Shadow client-scheduled breaks: stay idle (offers/skills tick passively) while on break.
            if (context.sidecars.breakHandler.isOnBreak) {
                context.waiting.sleep(BREAK_POLL_MS)
                continue
            }
            runner.tick()
            context.waiting.sleep(LOOP_DELAY_MS)
        }
    }

    private companion object {
        const val LOOP_DELAY_MS = 600L
        const val BREAK_POLL_MS = 2_000L
    }
}
