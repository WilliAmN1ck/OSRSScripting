package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.Walker
import com.osrsscripts.accountbuilder.engine.GameView
import com.osrsscripts.accountbuilder.engine.GatherResource
import com.osrsscripts.accountbuilder.engine.Requirements
import com.osrsscripts.accountbuilder.engine.Skill
import com.osrsscripts.accountbuilder.engine.TaskKey
import com.osrsscripts.accountbuilder.engine.TaskProgress
import com.osrsscripts.accountbuilder.view.SdkGameView
import com.osrsscripts.accountbuilder.view.SdkSkills
import org.tribot.script.sdk.Bank
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Log
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.GameObject
import org.tribot.script.sdk.types.WorldTile
import org.tribot.script.sdk.util.TribotRandom

/**
 * A gathering skill as a [BuilderTask] — chop / mine / etc. Complete once [skill] reaches the target
 * level; otherwise gather the best (highest-level) reachable selected, level-gated [GatherResource] — so
 * it upgrades automatically as the account levels — bank all-but-tool at the nearest bank when full, and
 * return to the remembered spot. When no [tool] is held it walks to the nearest bank and withdraws the
 * best usable one. Each step keys off observable game state, so it is re-entrant and safe to interrupt.
 * (Execute is SDK-coupled and verified live; the resource / tool models are pure and unit-tested.)
 */
internal class GatheringTask(
    keyValue: String,
    private val skill: Skill,
    private val gatherAction: String,
    private val tool: ToolModel,
    private val allowedResources: () -> Set<GatherResource>,
    private val targetLevel: () -> Int,
    initialSpot: WorldTile? = null,
) : BuilderTask {

    override val key = TaskKey(keyValue)
    override val requirements = Requirements()

    // Captured the first time we gather, or restored from the saved profile, so we can walk back to the
    // resource after banking or a restart — works wherever the user starts, no hardcoded tiles.
    private var spot: WorldTile? = initialSpot

    // Throttles the "no usable tool anywhere" warning so a misconfigured run logs once per interval.
    private var lastNoToolLogMs = 0L

    private val skillLabel = skill.name.lowercase().replaceFirstChar { it.uppercase() }

    /** The current gather anchor (for persistence), or null if we've never gathered and none was restored. */
    fun currentSpot(): WorldTile? = spot

    override fun isComplete(view: GameView): Boolean =
        view.skills.level(skill) >= targetLevel()

    // Not runnable with nothing selected. Tool availability is deliberately NOT gated here: when the
    // player has no tool, execute() actively fetches the best usable one from the bank, so the task must
    // be allowed to run to do that. ("Nothing selected" surfaces via the scheduler's status line.)
    override fun validate(view: GameView): Boolean =
        requirements.meets(view) && allowedResources().isNotEmpty()

    override fun progress(view: GameView): TaskProgress =
        TaskProgress("$skillLabel ${view.skills.level(skill)}/${targetLevel()}")

    override fun execute() {
        when {
            !tool.hasTool(SdkGameView) -> acquireTool()
            Inventory.isFull() -> bank()
            else -> gather()
        }
    }

    private fun gather() {
        if (Bank.isOpen()) {
            Bank.close() // never walk/gather with the bank interface open — it blocks walking
            return
        }
        if (MyPlayer.isAnimating()) return // already gathering

        if (allowedResources().isEmpty()) return // validate() gates this; guards the rare unselect-mid-tick race

        val target = findReachableResource()
        if (target == null) {
            val anchor = spot
            if (anchor == null) {
                Log.warn("No $skillLabel resource reachable and no spot remembered — start at the resource.")
                return
            }
            if (!Walker.walkTo(anchor)) {
                Log.warn("Walk back to the $skillLabel spot did not complete (walker returned false).")
            }
            return
        }

        spot = MyPlayer.getTile()
        if (target.interact(gatherAction)) {
            Waiting.waitUntil(GATHER_TIMEOUT_MS) { MyPlayer.isAnimating() || Inventory.isFull() }
        }
    }

    private fun bank() {
        if (!Bank.isOpen()) {
            if (!Bank.ensureOpen()) {
                Walker.walkToBank() // not at a bank — walk to the nearest (handles stairs), retry next tick
                return
            }
        }
        depositNonToolItems()
        Bank.close()
        Waiting.waitUntil(CLOSE_TIMEOUT_MS) { !Bank.isOpen() } // confirm closed before we walk off
    }

    /** Deposits every non-tool item type explicitly, each with a short, human-like gap. Bank must be open. */
    private fun depositNonToolItems() {
        val depositIds = Query.inventory()
            .filter { item -> !tool.isTool(item.name) }
            .toList()
            .map { it.id }
            .distinct()
        for (id in depositIds) {
            Bank.depositAll(id)
            Waiting.wait(TribotRandom.uniform(DEPOSIT_GAP_MIN_MS, DEPOSIT_GAP_MAX_MS))
        }
    }

    /**
     * Fetch the best tool the account can use from the bank when none is held. If we're standing at the
     * resource (one is reachable and no spot is remembered yet), remember the spot first so we can walk
     * back after banking. Then walk to the nearest bank, withdraw the highest-tier usable tool, and close.
     * If the bank holds no usable tool, logs once per interval and idles — the watchdog backstops it.
     */
    private fun acquireTool() {
        if (spot == null && findReachableResource() != null) {
            spot = MyPlayer.getTile() // at the resource with no tool — remember where to return
        }
        if (!Bank.isOpen()) {
            if (!Bank.ensureOpen()) {
                Walker.walkToBank()
                return
            }
        }
        val toWithdraw = tool.bestUsable(Bank.getAll().map { it.name }, skillLevel())
        if (toWithdraw == null) {
            logNoUsableTool()
            return // leave the bank open and idle; the watchdog stops a genuinely tool-less run
        }
        if (Inventory.isFull()) depositNonToolItems() // free a slot — we hold no tool, so nothing to keep
        if (Bank.withdraw(toWithdraw, 1)) {
            Waiting.waitUntil(WITHDRAW_TIMEOUT_MS) { tool.hasTool(SdkGameView) }
        }
        Bank.close()
        Waiting.waitUntil(CLOSE_TIMEOUT_MS) { !Bank.isOpen() }
    }

    /** The best (highest-level) reachable selected resource, or null if none is reachable from here. */
    private fun findReachableResource(): GameObject? =
        allowedResources()
            .sortedByDescending { it.levelReq }
            .firstNotNullOfOrNull { resource ->
                Query.gameObjects()
                    .actionEquals(gatherAction)
                    .filter { obj -> resource.matches(obj.name, obj.id) }
                    .isReachable
                    .findClosest()
                    .orElse(null)
            }

    private fun skillLevel(): Int = SdkSkills.toSdk(skill).getActualLevel()

    private fun logNoUsableTool() {
        val now = System.currentTimeMillis()
        if (now - lastNoToolLogMs >= NO_TOOL_LOG_INTERVAL_MS) {
            lastNoToolLogMs = now
            Log.warn("No usable $skillLabel tool in inventory, equipment, or bank — add one to continue.")
        }
    }

    private companion object {
        const val GATHER_TIMEOUT_MS = 8_000
        const val DEPOSIT_GAP_MIN_MS = 20
        const val DEPOSIT_GAP_MAX_MS = 80
        const val CLOSE_TIMEOUT_MS = 2_000
        const val WITHDRAW_TIMEOUT_MS = 3_000
        const val NO_TOOL_LOG_INTERVAL_MS = 30_000L
    }
}
