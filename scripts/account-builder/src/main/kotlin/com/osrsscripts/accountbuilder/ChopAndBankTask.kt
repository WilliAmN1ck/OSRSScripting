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
 * Phase 1 vertical-slice proof (intentionally throwaway): a single chop → bank loop that exercises
 * the SDK integration end-to-end — object [Query], interaction, banking, walking, and waits — before
 * the real task engine exists. Each step keys off observable game state (animating / inventory full /
 * bank reachable), so it is re-entrant and safe to interrupt.
 *
 * Targets only normal "Tree" objects (Woodcutting level 1) so any account can run it, and banks at
 * the nearest bank via the walker (handles obstacles/stairs), returning to the remembered chop spot.
 */
internal class ChopAndBankTask : Task {

    // Captured the first time we chop, so we can walk back after banking — no hardcoded tiles, works
    // wherever the user starts at the trees.
    private var chopSpot: WorldTile? = null

    override fun shouldRun(): Boolean = true

    override fun execute() {
        if (Inventory.isFull()) bank() else chop()
    }

    private fun chop() {
        if (MyPlayer.isAnimating()) return // already chopping

        val tree = Query.gameObjects()
            .nameEquals(TREE_NAME)
            .actionEquals("Chop down")
            .isReachable
            .findClosest()
            .orElse(null)

        if (tree == null) {
            val spot = chopSpot
            if (spot != null) {
                Walker.walkTo(spot) // returned from banking — head back to the trees
            } else {
                Log.warn("No '$TREE_NAME' reachable and no chop spot remembered — start at the trees.")
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
        Bank.depositAllExcept(*KEEP)
        Bank.close()
    }

    private companion object {
        // Normal trees only — Woodcutting level 1, so any account can chop them.
        const val TREE_NAME = "Tree"

        // Keep any axe; deposit everything else (logs, junk).
        val KEEP = arrayOf(
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe", "Dragon axe",
        )

        const val CHOP_TIMEOUT_MS = 8_000
    }
}
