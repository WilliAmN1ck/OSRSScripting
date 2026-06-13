package com.osrsscripts.accountbuilder.task

/**
 * Recognises Woodcutting axes by name so banking keeps the player's axe and deposits everything else.
 * Matches every axe tier — including crystal / infernal / 3rd-age / gilded and Forestry felling axes —
 * while deliberately NOT matching the pickaxe (which should be banked) or axe-type weapons.
 */
internal object Axes {
    fun isAxe(itemName: String): Boolean {
        val name = itemName.lowercase()
        return name.endsWith("axe") &&
            !name.contains("pickaxe") &&
            !name.contains("battleaxe")
    }
}
