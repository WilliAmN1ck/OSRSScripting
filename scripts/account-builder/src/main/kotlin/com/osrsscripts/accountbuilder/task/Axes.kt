package com.osrsscripts.accountbuilder.task

/**
 * Woodcutting axes. Recognises every axe tier — including crystal / infernal / 3rd-age / gilded and
 * Forestry felling axes — while deliberately NOT matching the pickaxe, axe-type weapons (battleaxe), or
 * throwing axes (thrownaxe), none of which can chop a tree. [toolReq] auto-withdraws only the standard
 * non-degradable progression (Bronze → Dragon, incl. felling variants); degradable/rare axes return null.
 */
internal object Axes : ToolModel {

    override fun isTool(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith("axe") &&
            !n.contains("pickaxe") &&
            !n.contains("battleaxe") &&
            !n.contains("thrownaxe")
    }

    override fun toolReq(name: String): Int? {
        val n = name.lowercase()
        return when {
            "dragon" in n -> 61
            "rune" in n -> 41
            "adamant" in n -> 31
            "mithril" in n -> 21
            "black" in n -> 11
            "steel" in n -> 6
            "bronze" in n || "iron" in n -> 1
            else -> null // unrecognised, or a degradable/rare axe we won't auto-use (crystal/infernal/3rd age/gilded)
        }
    }
}
