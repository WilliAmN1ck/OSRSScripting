package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.BuilderScheduler
import com.osrsscripts.accountbuilder.engine.Watchdog
import com.osrsscripts.accountbuilder.engine.profile.ProfileStore
import com.osrsscripts.accountbuilder.runner.MainBacklogTask
import com.osrsscripts.accountbuilder.task.WoodcuttingTask
import com.osrsscripts.accountbuilder.view.SdkGameView
import com.osrsscripts.core.humanize.AfkScheduler
import com.osrsscripts.core.humanize.DelayDistribution
import com.osrsscripts.core.humanize.FatigueScaler
import com.osrsscripts.core.task.TaskRunner
import org.tribot.automation.TribotScript
import org.tribot.automation.script.ScriptContext
import org.tribot.script.sdk.Login
import org.tribot.script.sdk.Skill
import org.tribot.script.sdk.antiban.Antiban
import org.tribot.script.sdk.util.ScriptSettings
import java.time.Duration
import java.time.Instant
import java.util.Random

/**
 * Entry point for the AIO Account Builder.
 *
 * Drives a sidebar-configured Woodcutting task through the pure engine ([BuilderScheduler] →
 * [WoodcuttingTask]) on the shared [TaskRunner], with antiban: Echo's action AI, break shadowing,
 * a fatigue-scaled loop cadence, and occasional look-away AFKs. Stops when every task is complete,
 * and a [Watchdog] stops the run if no Woodcutting XP is gained for a while (stuck). The task
 * engine, capability seams, and full task catalogue grow in later phases.
 */
class AccountBuilderScript : TribotScript {

    override fun execute(context: ScriptContext) {
        // Echo's built-in action humanization.
        Antiban.setScriptAiAntibanEnabled(true)

        val store = ProfileStore(ScriptSettings.getDefault().directory.toPath().resolve(PROFILE_FILE))
        val panel = AccountBuilderPanel(woodcuttingLevel())
        panel.applyProfile(store.load()) // restore the saved tree selection + target level
        context.sidebar.addSidebarTab(TAB_NAME, null, panel)

        val woodcutting = WoodcuttingTask(panel::selectedTrees, panel::targetLevel)
        val scheduler = BuilderScheduler(listOf(woodcutting))
        val runner = TaskRunner(listOf(MainBacklogTask(scheduler) { SdkGameView }))

        val random = Random()
        val startedAt = Instant.now()
        val fatigue = FatigueScaler(startedAt, FATIGUE_RAMP, FATIGUE_MAX)
        val cadence = DelayDistribution(CADENCE_MIN_MS, CADENCE_MAX_MS, random)
        val afk = AfkScheduler(startedAt, random, AFK_MIN_GAP, AFK_MAX_GAP, AFK_MIN, AFK_MAX)
        val watchdog = Watchdog(STALL_LIMIT_MS)
        var lastSavedProfile = panel.toProfile()

        try {
            while (!Thread.currentThread().isInterrupted) {
                // Shadow client-scheduled breaks: stay idle while on break. Reset the watchdog so the
                // break's zero-XP stretch doesn't count as a stall the moment we resume.
                if (context.sidecars.breakHandler.isOnBreak) {
                    watchdog.reset()
                    context.waiting.sleep(IDLE_POLL_MS)
                    continue
                }
                // Never act (or read skill levels) while logged out — the break/login handler
                // brings us back in; until then, stay idle (and don't count it as a stall).
                if (!Login.isLoggedIn()) {
                    watchdog.reset()
                    context.waiting.sleep(IDLE_POLL_MS)
                    continue
                }

                val now = Instant.now()

                // Every task done: announce and stop.
                if (scheduler.allComplete(SdkGameView)) {
                    context.logger.info("AIO Account Builder: all tasks complete — stopping.")
                    break
                }
                // No progress for too long: bail rather than spin forever (stuck/misconfigured).
                if (watchdog.evaluate(now.toEpochMilli(), woodcuttingXp()) == Watchdog.Decision.STOP) {
                    context.logger.warn("No Woodcutting XP gained for a while — stopping (stuck?).")
                    break
                }

                // Occasional look-away AFK, in chunks so a Stop stays responsive.
                val lookAway = afk.pollAt(now)
                if (lookAway.isPresent) {
                    var remaining = lookAway.get().toMillis()
                    while (remaining > 0 && !Thread.currentThread().isInterrupted) {
                        val chunk = minOf(remaining, AFK_CHUNK_MS)
                        context.waiting.sleep(chunk)
                        remaining -= chunk
                    }
                    continue
                }

                // Re-gate the tree checkboxes as Woodcutting levels up during the run.
                panel.setWoodcuttingLevel(woodcuttingLevel())
                // Persist config changes (tree selection / target) as the user makes them.
                val profile = panel.toProfile()
                if (profile != lastSavedProfile) {
                    runCatching { store.save(profile) }
                        .onFailure { context.logger.warn("Failed to save profile", it) }
                    lastSavedProfile = profile
                }
                runner.tick()
                context.waiting.sleep(Math.round(cadence.nextMs() * fatigue.multiplierAt(now)))
            }
        } finally {
            context.sidebar.removeSidebarTab(TAB_NAME)
        }
    }

    private fun woodcuttingLevel(): Int = Skill.WOODCUTTING.getActualLevel()

    // Watchdog progress signal. Woodcutting-specific for now (the only task); switch to total XP
    // once the backlog spans multiple skills, or a non-WC task would look like a WC stall.
    private fun woodcuttingXp(): Long = Skill.WOODCUTTING.getXp().toLong()

    private companion object {
        const val TAB_NAME = "Account Builder"
        const val PROFILE_FILE = "account-builder-profile.json"
        const val IDLE_POLL_MS = 2_000L
        const val CADENCE_MIN_MS = 400L
        const val CADENCE_MAX_MS = 900L
        const val FATIGUE_MAX = 1.6
        // Generous: a look-away AFK plus a long bank round-trip legitimately gains no WC XP, so a
        // tight window would false-stop a healthy run. Only a genuinely stuck run trips this. Tune at soak.
        const val STALL_LIMIT_MS = 10L * 60 * 1000
        const val AFK_CHUNK_MS = 1_000L
        val FATIGUE_RAMP: Duration = Duration.ofHours(3)
        val AFK_MIN_GAP: Duration = Duration.ofMinutes(12)
        val AFK_MAX_GAP: Duration = Duration.ofMinutes(24)
        val AFK_MIN: Duration = Duration.ofSeconds(20)
        val AFK_MAX: Duration = Duration.ofSeconds(90)
    }
}
