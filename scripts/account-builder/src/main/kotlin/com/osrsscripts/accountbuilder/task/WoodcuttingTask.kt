package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.TreeType
import com.osrsscripts.accountbuilder.Walker
import com.osrsscripts.accountbuilder.engine.GameView
import com.osrsscripts.accountbuilder.engine.Requirements
import com.osrsscripts.accountbuilder.engine.Skill
import com.osrsscripts.accountbuilder.engine.TaskKey
import com.osrsscripts.accountbuilder.engine.TaskProgress
import com.osrsscripts.accountbuilder.view.SdkGameView
import org.tribot.script.sdk.Bank
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Log
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.GameObject
import org.tribot.script.sdk.types.WorldTile
import org.tribot.script.sdk.util.TribotRandom
import org.tribot.script.sdk.Skill as SdkSkill

/**
 * Woodcutting as a [BuilderTask]: complete once Woodcutting reaches the target level; otherwise chop
 * the best (highest-level) reachable user-selected, level-gated tree — so it upgrades automatically as
 * the account levels — bank all-but-axe at the nearest bank when full, and return to the remembered
 * chop spot. Each step keys off observable game state, so it is re-entrant and safe to interrupt.
 * (Execute is SDK-coupled and verified live.)
 */
internal class WoodcuttingTask(
    private val allowedTrees: () -> Set<TreeType>,
    private val targetLevel: () -> Int,
) : BuilderTask {

    override val key = TaskKey("woodcutting")
    override val requirements = Requirements() // normal F2P trees need nothing beyond the tree itself

    // Captured the first time we chop, so we can walk back after banking — works wherever the user
    // starts at the trees, no hardcoded tiles.
    private var chopSpot: WorldTile? = null

    // Throttles the "no usable axe anywhere" warning so a misconfigured run logs once per interval.
    private var lastNoAxeLogMs = 0L

    override fun isComplete(view: GameView): Boolean =
        view.skills.level(Skill.WOODCUTTING) >= targetLevel()

    // Not runnable with no tree selected. Axe availability is deliberately NOT gated here: when the
    // player has no axe, execute() actively fetches the best usable one from the bank, so the task must
    // be allowed to run to do that. ("No tree selected" still surfaces via the scheduler's status line.)
    override fun validate(view: GameView): Boolean =
        requirements.meets(view) && allowedTrees().isNotEmpty()

    override fun progress(view: GameView): TaskProgress =
        TaskProgress("Woodcutting ${view.skills.level(Skill.WOODCUTTING)}/${targetLevel()}")

    override fun execute() {
        when {
            !Axes.hasAxe(SdkGameView) -> acquireAxe()
            Inventory.isFull() -> bank()
            else -> chop()
        }
    }

    private fun chop() {
        if (Bank.isOpen()) {
            Bank.close() // never walk/chop with the bank interface open — it blocks walking
            return
        }
        if (MyPlayer.isAnimating()) return // already chopping

        if (allowedTrees().isEmpty()) return // validate() gates this; guards the rare unselect-mid-tick race

        val tree = findReachableTree()
        if (tree == null) {
            val spot = chopSpot
            if (spot == null) {
                Log.warn("No tree reachable and no chop spot remembered — start at the trees.")
                return
            }
            if (!Walker.walkTo(spot)) {
                Log.warn("Walk back to the chop spot did not complete (walker returned false).")
            }
            return
        }

        chopSpot = MyPlayer.getTile()
        if (tree.interact("Chop down")) {
            Waiting.waitUntil(CHOP_TIMEOUT_MS) { MyPlayer.isAnimating() || Inventory.isFull() }
        }
    }

    private fun bank() {
        if (!Bank.isOpen()) {
            if (!Bank.ensureOpen()) {
                Walker.walkToBank() // not at a bank — walk to the nearest (handles stairs), retry next tick
                return
            }
        }
        depositNonAxeItems()
        Bank.close()
        Waiting.waitUntil(CLOSE_TIMEOUT_MS) { !Bank.isOpen() } // confirm closed before we walk off
    }

    /** Deposits every non-axe item type explicitly, each with a short, human-like gap. Bank must be open. */
    private fun depositNonAxeItems() {
        val depositIds = Query.inventory()
            .filter { item -> !Axes.isAxe(item.name) }
            .toList()
            .map { it.id }
            .distinct()
        for (id in depositIds) {
            Bank.depositAll(id)
            Waiting.wait(TribotRandom.uniform(DEPOSIT_GAP_MIN_MS, DEPOSIT_GAP_MAX_MS))
        }
    }

    /**
     * Fetch the best axe the account can use from the bank when none is held. If we're standing at the
     * trees (a selected tree is reachable and no spot is remembered yet), remember the spot first so we
     * can walk back after banking. Then walk to the nearest bank, withdraw the highest-tier axe whose
     * Woodcutting requirement is met, and close. If the bank holds no usable axe, logs once per interval
     * and idles — the watchdog backstops a genuinely axe-less setup.
     */
    private fun acquireAxe() {
        if (chopSpot == null && findReachableTree() != null) {
            chopSpot = MyPlayer.getTile() // at the trees with no axe — remember where to return
        }
        if (!Bank.isOpen()) {
            if (!Bank.ensureOpen()) {
                Walker.walkToBank() // not at a bank — walk to the nearest (handles stairs), retry next tick
                return
            }
        }
        val axe = Axes.bestUsableAxe(Bank.getAll().map { it.name }, SdkSkill.WOODCUTTING.getActualLevel())
        if (axe == null) {
            logNoUsableAxe()
            return // leave the bank open and idle; the watchdog stops a genuinely axe-less run
        }
        // Free a slot if the pack is full — we hold no axe here, so there's nothing to keep.
        if (Inventory.isFull()) depositNonAxeItems()
        if (Bank.withdraw(axe, 1)) {
            Waiting.waitUntil(WITHDRAW_TIMEOUT_MS) { Axes.hasAxe(SdkGameView) }
        }
        Bank.close()
        Waiting.waitUntil(CLOSE_TIMEOUT_MS) { !Bank.isOpen() }
    }

    /** The best (highest-level) reachable selected tree, or null if none is reachable from here. */
    private fun findReachableTree(): GameObject? =
        allowedTrees()
            .sortedByDescending { it.levelReq }
            .firstNotNullOfOrNull { type ->
                Query.gameObjects()
                    .actionEquals("Chop down")
                    .filter { obj -> type.matches(obj.name) }
                    .isReachable
                    .findClosest()
                    .orElse(null)
            }

    private fun logNoUsableAxe() {
        val now = System.currentTimeMillis()
        if (now - lastNoAxeLogMs >= NO_AXE_LOG_INTERVAL_MS) {
            lastNoAxeLogMs = now
            Log.warn("No usable Woodcutting axe in inventory, equipment, or bank — add one to continue.")
        }
    }

    private companion object {
        const val CHOP_TIMEOUT_MS = 8_000
        const val DEPOSIT_GAP_MIN_MS = 20
        const val DEPOSIT_GAP_MAX_MS = 80
        const val CLOSE_TIMEOUT_MS = 2_000
        const val WITHDRAW_TIMEOUT_MS = 3_000
        const val NO_AXE_LOG_INTERVAL_MS = 30_000L
    }
}
