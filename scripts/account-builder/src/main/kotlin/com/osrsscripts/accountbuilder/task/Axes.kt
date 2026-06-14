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

    /**
     * The best (highest Woodcutting requirement) axe in [bankItemNames] that [woodcuttingLevel] can use,
     * or null if none. Used to withdraw an axe when the player has none — the bot chops from inventory,
     * so only the Woodcutting requirement matters (wielding/Attack level is irrelevant). Axe tiers it
     * doesn't recognise are skipped rather than risk withdrawing one whose requirement is unknown.
     */
    fun bestUsableAxe(bankItemNames: List<String>, woodcuttingLevel: Int): String? =
        bankItemNames
            .filter(::isAxe)
            .mapNotNull { name -> woodcuttingReq(name)?.let { req -> name to req } }
            .filter { (_, req) -> woodcuttingLevel >= req }
            .maxByOrNull { (_, req) -> req }
            ?.first

    /**
     * Woodcutting level required to chop with the named axe, or null if we won't auto-withdraw it.
     * Recognises the standard non-degradable progression (Bronze → Dragon, including the Forestry
     * felling-axe variants, which share these tiers). Deliberately returns null for degradable or
     * rare/valuable axes — Crystal (degrades, needs shards), Infernal (degrades), and 3rd age / Gilded
     * (rare cosmetics) — so a build never auto-burns or risks one; a plain equal-tier axe is preferred.
     */
    private fun woodcuttingReq(axeName: String): Int? {
        val n = axeName.lowercase()
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
