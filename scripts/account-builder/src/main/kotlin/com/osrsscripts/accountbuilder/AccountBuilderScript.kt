package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.BuilderScheduler
import com.osrsscripts.accountbuilder.runner.MainBacklogTask
import com.osrsscripts.accountbuilder.task.WoodcuttingTask
import com.osrsscripts.accountbuilder.view.SdkGameView
import com.osrsscripts.core.task.TaskRunner
import org.tribot.automation.TribotScript
import org.tribot.automation.script.ScriptContext
import org.tribot.script.sdk.Login
import org.tribot.script.sdk.Skill
import org.tribot.script.sdk.antiban.Antiban

/**
 * Entry point for the AIO Account Builder.
 *
 * Current slice: a Woodcutting chop → bank loop driven by a sidebar tree-selection panel
 * ([AccountBuilderPanel]), on the shared [TaskRunner] with antiban + break shadowing. The task
 * engine, capability seams, and the full task catalog come in later phases
 * (see docs/plans/aio-account-builder/plan.md).
 */
class AccountBuilderScript : TribotScript {

    override fun execute(context: ScriptContext) {
        // Echo's built-in action humanization.
        Antiban.setScriptAiAntibanEnabled(true)

        val panel = AccountBuilderPanel(woodcuttingLevel())
        context.sidebar.addSidebarTab(TAB_NAME, null, panel)

        val woodcutting = WoodcuttingTask(panel::selectedTrees, panel::targetLevel)
        val scheduler = BuilderScheduler(listOf(woodcutting))
        val runner = TaskRunner(listOf(MainBacklogTask(scheduler) { SdkGameView }))
        try {
            while (!Thread.currentThread().isInterrupted) {
                // Shadow client-scheduled breaks: stay idle while on break.
                if (context.sidecars.breakHandler.isOnBreak) {
                    context.waiting.sleep(BREAK_POLL_MS)
                    continue
                }
                // Never act (or read skill levels) while logged out — the break/login handler
                // brings us back in; until then, stay idle.
                if (!Login.isLoggedIn()) {
                    context.waiting.sleep(BREAK_POLL_MS)
                    continue
                }
                // Re-gate the tree checkboxes as Woodcutting levels up during the run.
                panel.setWoodcuttingLevel(woodcuttingLevel())
                runner.tick()
                context.waiting.sleep(LOOP_DELAY_MS)
            }
        } finally {
            context.sidebar.removeSidebarTab(TAB_NAME)
        }
    }

    private fun woodcuttingLevel(): Int = Skill.WOODCUTTING.getActualLevel()

    private companion object {
        const val TAB_NAME = "Account Builder"
        const val LOOP_DELAY_MS = 600L
        const val BREAK_POLL_MS = 2_000L
    }
}
