package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.BuilderScheduler
import com.osrsscripts.accountbuilder.engine.Watchdog
import com.osrsscripts.accountbuilder.engine.profile.BuildProfile
import com.osrsscripts.accountbuilder.engine.profile.ProfileStore
import com.osrsscripts.accountbuilder.engine.profile.TileCodec
import com.osrsscripts.accountbuilder.engine.profile.getTaskParam
import com.osrsscripts.accountbuilder.runner.MainBacklogTask
import com.osrsscripts.accountbuilder.task.GatheringTask
import com.osrsscripts.accountbuilder.task.MINING_KEY
import com.osrsscripts.accountbuilder.task.WOODCUTTING_KEY
import com.osrsscripts.accountbuilder.task.miningTask
import com.osrsscripts.accountbuilder.task.woodcuttingTask
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
 * Drives one sidebar-configured [GatheringTask] per skill (Woodcutting + Mining) through the pure engine
 * ([BuilderScheduler]) on the shared [TaskRunner], with antiban: Echo's action AI, break shadowing, a
 * fatigue-scaled loop cadence, and occasional look-away AFKs. Stops when every task is complete, and a
 * [Watchdog] stops the run if no XP is gained across the configured skills for a while (stuck). Each
 * skill's config (selection + target) and gather anchor persist to one profile. The task catalogue grows
 * in later phases.
 */
class AccountBuilderScript : TribotScript {

    override fun execute(context: ScriptContext) {
        // Echo's built-in action humanization.
        Antiban.setScriptAiAntibanEnabled(true)

        val store = ProfileStore(ScriptSettings.getDefault().directory.toPath().resolve(PROFILE_FILE))
        val savedProfile = store.load()

        // One config tab per skill. Woodcutting pre-selects Normal (preserving the prior single-skill
        // default); Mining starts with nothing selected, so it is opt-in and a Woodcutting-only user is
        // unaffected until they tick an ore.
        val wcPanel = GatherConfigPanel(
            title = "Trees to cut",
            skillLabel = "Woodcutting",
            resources = TreeType.values().toList(),
            taskKey = WOODCUTTING_KEY,
            resourceParamKey = WC_RESOURCE_PARAM,
            initialLevel = woodcuttingLevel(),
            defaultSelected = setOf(TreeType.NORMAL.id),
        )
        val minePanel = GatherConfigPanel(
            title = "Rocks to mine",
            skillLabel = "Mining",
            resources = RockType.values().toList(),
            taskKey = MINING_KEY,
            resourceParamKey = MINE_RESOURCE_PARAM,
            initialLevel = miningLevel(),
        )
        wcPanel.applyProfile(savedProfile)
        minePanel.applyProfile(savedProfile)
        context.sidebar.addSidebarTab(WC_TAB, null, wcPanel)
        context.sidebar.addSidebarTab(MINE_TAB, null, minePanel)

        val woodcutting = woodcuttingTask({ wcPanel.selectedResources() }, wcPanel::targetLevel, savedSpot(savedProfile, WOODCUTTING_KEY))
        val mining = miningTask({ minePanel.selectedResources() }, minePanel::targetLevel, savedSpot(savedProfile, MINING_KEY))
        val scheduler = BuilderScheduler(listOf(woodcutting, mining))
        val runner = TaskRunner(listOf(MainBacklogTask(scheduler) { SdkGameView }))
        val skills = listOf(
            SkillState(wcPanel, woodcutting, WOODCUTTING_KEY),
            SkillState(minePanel, mining, MINING_KEY),
        )

        val random = Random()
        val startedAt = Instant.now()
        val fatigue = FatigueScaler(startedAt, FATIGUE_RAMP, FATIGUE_MAX)
        val cadence = DelayDistribution(CADENCE_MIN_MS, CADENCE_MAX_MS, random)
        val afk = AfkScheduler(startedAt, random, AFK_MIN_GAP, AFK_MAX_GAP, AFK_MIN, AFK_MAX)
        val watchdog = Watchdog(STALL_LIMIT_MS)
        // Seed with the loaded config + anchors so the first tick doesn't redundantly re-save.
        var lastSavedProfile = composeProfile(savedProfile, skills) { savedProfile.getTaskParam(it.key, CHOP_TILE_PARAM) }

        try {
            while (!Thread.currentThread().isInterrupted) {
                // Shadow client-scheduled breaks: stay idle while on break. Reset the watchdog so the
                // break's zero-XP stretch doesn't count as a stall the moment we resume.
                if (context.sidecars.breakHandler.isOnBreak) {
                    watchdog.reset()
                    context.waiting.sleep(IDLE_POLL_MS)
                    continue
                }
                // Never act (or read skill levels) while logged out — the break/login handler brings us
                // back in; until then, stay idle (and don't count it as a stall).
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
                if (watchdog.evaluate(now.toEpochMilli(), trainedXp()) == Watchdog.Decision.STOP) {
                    context.logger.warn("No skill XP gained for a while — stopping (stuck?).")
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

                // Re-gate each skill's checkboxes as its level rises during the run.
                wcPanel.setSkillLevel(woodcuttingLevel())
                minePanel.setSkillLevel(miningLevel())
                // Persist config (selection / target) immediately, and each gather anchor when it
                // relocates, so a restart returns to the resource without spamming the disk.
                val profile = composeProfile(savedProfile, skills) { stabilizedSpot(it.task.currentSpot(), it.key, lastSavedProfile) }
                if (profile != lastSavedProfile) {
                    runCatching { store.save(profile) }
                        .onFailure { context.logger.warn("Failed to save profile", it) }
                    lastSavedProfile = profile
                }
                runner.tick()
                context.waiting.sleep(Math.round(cadence.nextMs() * fatigue.multiplierAt(now)))
            }
        } finally {
            context.sidebar.removeSidebarTab(WC_TAB)
            context.sidebar.removeSidebarTab(MINE_TAB)
        }
    }

    private fun woodcuttingLevel(): Int = Skill.WOODCUTTING.getActualLevel()

    private fun miningLevel(): Int = Skill.MINING.getActualLevel()

    // Watchdog progress signal: total XP across the configured skills, so a non-Woodcutting task no
    // longer looks like a Woodcutting stall.
    private fun trainedXp(): Long = (Skill.WOODCUTTING.getXp() + Skill.MINING.getXp()).toLong()

    /** A configured skill: its config panel, its running task, and its persistence key. */
    private class SkillState(val panel: GatherConfigPanel, val task: GatheringTask, val key: String)

    private fun savedSpot(profile: BuildProfile, key: String): WorldTile? =
        profile.getTaskParam(key, CHOP_TILE_PARAM)
            ?.let(TileCodec::parse)
            ?.let { (x, y, plane) -> WorldTile(x, y, plane) }

    /**
     * Builds the profile to persist: each skill's config (selection / target) plus its gather anchor,
     * preserving the loaded shuffleSeed so a save never silently drops a persisted field. [spotOf]
     * supplies each skill's anchor string (or null to omit it).
     */
    private fun composeProfile(loaded: BuildProfile, skills: List<SkillState>, spotOf: (SkillState) -> String?): BuildProfile {
        val tasks = skills.map { s ->
            val base = s.panel.toProfile().tasks.first()
            val spot = spotOf(s)
            if (spot == null) base else base.copy(params = base.params + (CHOP_TILE_PARAM to spot))
        }
        return BuildProfile(shuffleSeed = loaded.shuffleSeed, tasks = tasks)
    }

    // Keeps a skill's last-saved anchor unless the player has genuinely relocated (different plane or more
    // than CHOP_TILE_RESAVE_THRESHOLD tiles away), so config saves stay instant while the anchor only
    // re-persists on a real move rather than on every step between resources.
    private fun stabilizedSpot(current: WorldTile?, key: String, lastSaved: BuildProfile): String? {
        val previous = lastSaved.getTaskParam(key, CHOP_TILE_PARAM)
        if (current == null) return previous
        val prev = TileCodec.parse(previous)
        val relocated = prev == null ||
            prev.third != current.plane ||
            maxOf(abs(prev.first - current.x), abs(prev.second - current.y)) > CHOP_TILE_RESAVE_THRESHOLD
        return if (relocated) TileCodec.format(current.x, current.y, current.plane) else previous
    }

    private companion object {
        const val WC_TAB = "Woodcutting"
        const val MINE_TAB = "Mining"
        const val PROFILE_FILE = "account-builder-profile.json"
        const val WC_RESOURCE_PARAM = "trees"
        const val MINE_RESOURCE_PARAM = "rocks"
        const val CHOP_TILE_PARAM = "chopTile" // per-skill gather anchor (kept named "chopTile" for back-compat)
        const val CHOP_TILE_RESAVE_THRESHOLD = 8 // tiles; re-persist the anchor only on a real relocation
        const val IDLE_POLL_MS = 2_000L
        const val CADENCE_MIN_MS = 400L
        const val CADENCE_MAX_MS = 900L
        const val FATIGUE_MAX = 1.6
        // Generous: a look-away AFK plus a long bank round-trip legitimately gains no XP, so a tight
        // window would false-stop a healthy run. Only a genuinely stuck run trips this. Tune at soak.
        const val STALL_LIMIT_MS = 10L * 60 * 1000
        const val AFK_CHUNK_MS = 1_000L
        val FATIGUE_RAMP: Duration = Duration.ofHours(3)
        val AFK_MIN_GAP: Duration = Duration.ofMinutes(12)
        val AFK_MAX_GAP: Duration = Duration.ofMinutes(24)
        val AFK_MIN: Duration = Duration.ofSeconds(20)
        val AFK_MAX: Duration = Duration.ofSeconds(90)
    }
}
