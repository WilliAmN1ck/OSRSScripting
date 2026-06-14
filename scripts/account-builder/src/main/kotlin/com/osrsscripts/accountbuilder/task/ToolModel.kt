package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.GameView

/**
 * A gathering tool family (axes, pickaxes, …). Concrete models supply only the name predicate
 * ([isTool]) and the tier→required-level map ([toolReq]); the "do I have one" and "which is the best
 * usable one in the bank" logic is shared. Tools whose [toolReq] is null (unrecognised, degradable, or
 * rare/valuable) are never auto-withdrawn.
 */
interface ToolModel {
    fun isTool(name: String): Boolean

    /** Skill level required to use the named tool, or null to never auto-withdraw it. */
    fun toolReq(name: String): Int?

    /** True if a usable tool is held — in inventory or worn. */
    fun hasTool(view: GameView): Boolean =
        view.inventory.itemNames().any(::isTool) || view.equipment.itemNames().any(::isTool)

    /** The best (highest requirement ≤ [skillLevel]) recognised tool in [bankItemNames], or null. */
    fun bestUsable(bankItemNames: List<String>, skillLevel: Int): String? =
        bankItemNames
            .filter(::isTool)
            .mapNotNull { name -> toolReq(name)?.let { req -> name to req } }
            .filter { (_, req) -> skillLevel >= req }
            .maxByOrNull { (_, req) -> req }
            ?.first
}
