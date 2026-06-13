package com.osrsscripts.accountbuilder

import net.runelite.api.coords.WorldPoint
import org.tribot.automation.script.addon.dentistwalker.WalkingCondition
import org.tribot.script.sdk.AutomationSdk
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Options
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.types.WorldTile

/**
 * Minimal walking helper over the DentistWalker addon (mirrors the TRiBot community examples). Walks
 * to a tile or to the nearest bank, enabling run when it is worth it. Later phases promote this into
 * the shared `actions/` layer.
 */
internal object Walker {

    fun walkTo(tile: WorldTile): Boolean =
        dentistWalker().walkTo(tile.toWorldPoint()) { runCondition() }

    /** Walks to the nearest bank, pathing through obstacles/stairs (e.g. the Lumbridge upstairs bank). */
    fun walkToBank(): Boolean =
        dentistWalker().walkToBank { runCondition() }

    private fun dentistWalker() = AutomationSdk.getContext().addonLibraries.dentistWalker

    private fun runCondition(): WalkingCondition.State {
        enableRun()
        return WalkingCondition.State.CONTINUE
    }

    private fun enableRun() {
        if (Options.isRunEnabled()) return
        if (MyPlayer.getRunEnergy() < MIN_RUN_ENERGY) return
        if (Options.setRunEnabled(true)) {
            Waiting.waitUntil(RUN_TOGGLE_TIMEOUT_MS) { Options.isRunEnabled() }
        }
    }

    private fun WorldTile.toWorldPoint(): WorldPoint = WorldPoint(x, y, plane)

    private const val MIN_RUN_ENERGY = 10
    private const val RUN_TOGGLE_TIMEOUT_MS = 2_000
}
