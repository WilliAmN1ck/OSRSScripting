package com.osrsscripts.accountbuilder.task

/**
 * Mining pickaxes. [isTool] recognises any pickaxe; [toolReq] auto-withdraws only the standard
 * non-degradable progression (Bronze → Dragon) — degradable/rare picks (crystal, infernal, 3rd age,
 * gilded, imcando) return null so a build never auto-burns or risks one. Mining-level-to-use follows the
 * OSRS wield-Attack + 1 pattern (identical numbers to the axe ladder).
 */
internal object Picks : ToolModel {

    override fun isTool(name: String): Boolean = name.lowercase().endsWith("pickaxe")

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
            else -> null // unrecognised, or degradable/rare (crystal/infernal/3rd age/gilded/imcando) — don't auto-use
        }
    }
}
