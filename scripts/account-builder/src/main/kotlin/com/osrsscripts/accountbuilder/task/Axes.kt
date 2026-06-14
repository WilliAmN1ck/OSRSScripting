package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.GameView

/**
 * Recognises Woodcutting axes by name so banking keeps the player's axe and deposits everything else.
 * Matches every axe tier — including crystal / infernal / 3rd-age / gilded and Forestry felling axes —
 * while deliberately NOT matching the pickaxe, axe-type weapons (battleaxe), or throwing axes
 * (thrownaxe) — none of which can chop a tree.
 */
internal object Axes {
    fun isAxe(itemName: String): Boolean {
        val name = itemName.lowercase()
        return name.endsWith("axe") &&
            !name.contains("pickaxe") &&
            !name.contains("battleaxe") &&
            !name.contains("thrownaxe")
    }

    /** True if the account has a Woodcutting axe to chop with — held in inventory or worn. */
    fun hasAxe(view: GameView): Boolean =
        view.inventory.itemNames().any(::isAxe) || view.equipment.itemNames().any(::isAxe)
}
