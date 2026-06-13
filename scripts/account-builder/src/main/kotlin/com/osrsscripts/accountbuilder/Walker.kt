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
 * to a tile, enabling run when it is worth it. Phase 1 uses it as a fallback when the target object
 * is not already reachable; later phases promote this into the shared `actions/` layer.
 */
internal object Walker {

    fun walkTo(tile: WorldTile): Boolean =
        AutomationSdk.getContext().addonLibraries.dentistWalker.walkTo(tile.toWorldPoint()) {
            enableRun()
            WalkingCondition.State.CONTINUE
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
