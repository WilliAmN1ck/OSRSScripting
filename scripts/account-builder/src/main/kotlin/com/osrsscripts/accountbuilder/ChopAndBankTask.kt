package com.osrsscripts.accountbuilder

import com.osrsscripts.core.task.Task
import org.tribot.script.sdk.Bank
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Log
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.WorldTile

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
            chopSpot?.let { Walker.walkTo(it) } // returned from banking — head back to the trees
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
        // A single depositAllExcept can race the game tick (only one item moves before we close), so
        // deposit until nothing depositable remains — confirming the inventory is clear — then close.
        var attempts = 0
        while (hasDepositableItems() && attempts++ < MAX_DEPOSIT_ATTEMPTS) {
            Bank.depositAllExcept(*KEEP)
            Waiting.waitUntil(DEPOSIT_TIMEOUT_MS) { !hasDepositableItems() }
        }
        Bank.close()
    }

    /** Whether the inventory still holds anything other than a kept axe (i.e. logs/junk to bank). */
    private fun hasDepositableItems(): Boolean =
        Query.inventory()
            .filter { item -> KEEP.none { keep -> keep.equals(item.name, ignoreCase = true) } }
            .isAny

    private companion object {
        // Keep any axe; deposit everything else (logs, junk).
        val KEEP = arrayOf(
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe", "Dragon axe",
        )

        const val CHOP_TIMEOUT_MS = 8_000
        const val MAX_DEPOSIT_ATTEMPTS = 5
        const val DEPOSIT_TIMEOUT_MS = 2_000
    }
}
