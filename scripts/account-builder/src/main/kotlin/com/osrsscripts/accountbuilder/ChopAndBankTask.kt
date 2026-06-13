package com.osrsscripts.accountbuilder

import com.osrsscripts.core.task.Task
import org.tribot.script.sdk.Bank
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.WorldTile

/**
 * Phase 1 vertical-slice proof (intentionally throwaway): a single hardcoded chop → bank loop that
 * exercises the SDK integration end-to-end — object [Query], interaction, banking, walking, and
 * waits — before the real task engine exists. Each step keys off observable game state (animating /
 * inventory full / bank reachable), so it is re-entrant and safe to interrupt.
 *
 * The exact spot is dialed in during live verification; the defaults are a known F2P,
 * bank-adjacent, non-depleting spot (Draynor Village willows next to the bank).
 */
internal class ChopAndBankTask : Task {

    override fun shouldRun(): Boolean = true

    override fun execute() {
        if (Inventory.isFull()) bank() else chop()
    }

    private fun chop() {
        if (MyPlayer.isAnimating()) return // already chopping

        val tree = Query.gameObjects()
            .actionEquals("Chop down")
            .isReachable
            .findClosest()
            .orElse(null)

        if (tree == null) {
            Walker.walkTo(TREE_TILE)
            return
        }

        if (tree.interact("Chop down")) {
            Waiting.waitUntil(CHOP_TIMEOUT_MS) { MyPlayer.isAnimating() || Inventory.isFull() }
        }
    }

    private fun bank() {
        if (!Bank.isOpen()) {
            if (!Query.gameObjects().actionEquals("Bank").isReachable.isAny) {
                Walker.walkTo(BANK_TILE)
                return
            }
            if (!Bank.ensureOpen()) return
        }
        Bank.depositAllExcept(*KEEP)
        Bank.close()
    }

    private companion object {
        // Phase 1 throwaway defaults — confirm the spot/tiles live. Draynor willows + bank (F2P).
        val TREE_TILE = WorldTile(3087, 3234, 0)
        val BANK_TILE = WorldTile(3092, 3243, 0)

        // Keep any axe in the inventory; deposit everything else (logs, junk).
        val KEEP = arrayOf(
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe", "Dragon axe",
        )

        const val CHOP_TIMEOUT_MS = 8_000
    }
}
