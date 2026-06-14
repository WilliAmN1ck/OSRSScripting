package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.BuilderScheduler
import com.osrsscripts.accountbuilder.engine.Watchdog
import com.osrsscripts.accountbuilder.engine.profile.BuildProfile
import com.osrsscripts.accountbuilder.engine.profile.ProfileStore
import com.osrsscripts.accountbuilder.engine.profile.TileCodec
import com.osrsscripts.accountbuilder.engine.profile.withTaskParam
import com.osrsscripts.accountbuilder.runner.MainBacklogTask
import com.osrsscripts.accountbuilder.task.WOODCUTTING_KEY
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
import org.tribot.script.sdk.types.WorldTile
import org.tribot.script.sdk.util.ScriptSettings
import java.time.Duration
import java.time.Instant
import java.util.Random
import kotlin.math.abs

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
        val savedProfile = store.load()
        val panel = AccountBuilderPanel(woodcuttingLevel())
        panel.applyProfile(savedProfile) // restore the saved tree selection + target level
        context.sidebar.addSidebarTab(TAB_NAME, null, panel)

        // Restore the last chop anchor so a restart (or a cold start at a bank) walks back to the trees.
        val savedChopSpot = savedProfile.chopTileParam()
            ?.let(TileCodec::parse)
            ?.let { (x, y, plane) -> WorldTile(x, y, plane) }
        val woodcutting = WoodcuttingTask(panel::selectedTrees, panel::targetLevel, savedChopSpot)
        val scheduler = BuilderScheduler(listOf(woodcutting))
        val runner = TaskRunner(listOf(MainBacklogTask(scheduler) { SdkGameView }))

        val random = Random()
        val startedAt = Instant.now()
        val fatigue = FatigueScaler(startedAt, FATIGUE_RAMP, FATIGUE_MAX)
        val cadence = DelayDistribution(CADENCE_MIN_MS, CADENCE_MAX_MS, random)
        val afk = AfkScheduler(startedAt, random, AFK_MIN_GAP, AFK_MAX_GAP, AFK_MIN, AFK_MAX)
        val watchdog = Watchdog(STALL_LIMIT_MS)
        // Seed with the loaded chop anchor so we don't redundantly re-save it on the first tick.
        var lastSavedProfile = composeProfile(panel, savedProfile, savedChopSpot?.let { TileCodec.format(it.x, it.y, it.plane) })

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
                // Persist config (trees / target) immediately, and the chop anchor when it relocates, so
                // a restart returns to the trees without spamming the disk with tree-to-tree jitter.
                val profile = composeProfile(panel, savedProfile, stabilizedChopTile(woodcutting.currentChopSpot(), lastSavedProfile))
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

    // Builds the profile to persist: the panel's config (trees / target) plus the chop-tile anchor,
    // preserving the loaded shuffleSeed so a save never silently drops a persisted field.
    private fun composeProfile(panel: AccountBuilderPanel, loaded: BuildProfile, chopTileParam: String?): BuildProfile =
        panel.toProfile()
            .copy(shuffleSeed = loaded.shuffleSeed)
            .withTaskParam(WOODCUTTING_KEY, CHOP_TILE_PARAM, chopTileParam)

    private fun BuildProfile.chopTileParam(): String? =
        tasks.firstOrNull { it.key == WOODCUTTING_KEY }?.params?.get(CHOP_TILE_PARAM)

    // Keeps the last-saved chop anchor unless the player has genuinely relocated (different plane or more
    // than CHOP_TILE_RESAVE_THRESHOLD tiles away), so config saves stay instant while the chop tile only
    // re-persists on a real move rather than on every tree-to-tree step.
    private fun stabilizedChopTile(current: WorldTile?, lastSaved: BuildProfile): String? {
        val previous = lastSaved.chopTileParam()
        if (current == null) return previous // nothing new to record; keep what we had
        val prev = TileCodec.parse(previous)
        val relocated = prev == null ||
            prev.third != current.plane ||
            maxOf(abs(prev.first - current.x), abs(prev.second - current.y)) > CHOP_TILE_RESAVE_THRESHOLD
        return if (relocated) TileCodec.format(current.x, current.y, current.plane) else previous
    }

    private companion object {
        const val TAB_NAME = "Account Builder"
        const val PROFILE_FILE = "account-builder-profile.json"
        const val CHOP_TILE_PARAM = "chopTile"
        const val CHOP_TILE_RESAVE_THRESHOLD = 8 // tiles; re-persist the anchor only on a real relocation
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
