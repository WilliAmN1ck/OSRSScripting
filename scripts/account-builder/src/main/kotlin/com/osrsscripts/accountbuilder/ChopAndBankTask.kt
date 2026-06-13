package com.osrsscripts.accountbuilder

import com.osrsscripts.core.task.Task
import org.tribot.script.sdk.Bank
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Log
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.WorldTile
import org.tribot.script.sdk.util.TribotRandom

/**
 * Chop → bank loop. Cuts the nearest reachable tree among the user-selected, level-qualified
 * [TreeType]s (from the sidebar), banks at the nearest bank when full (walker handles obstacles /
 * stairs), then returns to the remembered chop spot. Each step keys off observable game state
 * (animating / inventory full / bank reachable), so it is re-entrant and safe to interrupt.
 */
internal class ChopAndBankTask(
    private val allowedTrees: () -> Set<TreeType>,
) : Task {

    // Captured the first time we chop, so we can walk back after banking — works wherever the user
    // starts at the trees, no hardcoded tiles.
    private var chopSpot: WorldTile? = null

    override fun shouldRun(): Boolean = true

    override fun execute() {
        if (Inventory.isFull()) bank() else chop()
    }

    private fun chop() {
        if (Bank.isOpen()) {
            Bank.close() // never walk/chop with the bank interface open — it blocks walking
            return
        }
        if (MyPlayer.isAnimating()) return // already chopping

        val allowed = allowedTrees()
        if (allowed.isEmpty()) {
            Log.warn("No tree types selected within your Woodcutting level — pick one in the sidebar.")
            return
        }

        val tree = Query.gameObjects()
            .actionEquals("Chop down")
            .filter { obj -> allowed.any { it.matches(obj.name) } }
            .isReachable
            .findClosest()
            .orElse(null)

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
        // Deposit every non-axe item type explicitly, each with a short, human-like gap. depositAll
        // per item id is fast and reliably clears *all* types (logs plus any stray pickaxe/arrows),
        // unlike depositAllExcept which moved only one type per call.
        val depositIds = Query.inventory()
            .filter { item -> KEEP.none { keep -> keep.equals(item.name, ignoreCase = true) } }
            .toList()
            .map { it.id }
            .distinct()
        for (id in depositIds) {
            Bank.depositAll(id)
            Waiting.wait(TribotRandom.uniform(DEPOSIT_GAP_MIN_MS, DEPOSIT_GAP_MAX_MS))
        }
        Bank.close()
        Waiting.waitUntil(CLOSE_TIMEOUT_MS) { !Bank.isOpen() } // confirm closed before we walk off
    }

    private companion object {
        // Keep any axe; deposit everything else (logs, junk, stray tools/ammo).
        val KEEP = arrayOf(
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe", "Dragon axe",
        )

        const val CHOP_TIMEOUT_MS = 8_000
        const val DEPOSIT_GAP_MIN_MS = 20
        const val DEPOSIT_GAP_MAX_MS = 80
        const val CLOSE_TIMEOUT_MS = 2_000
    }
}
