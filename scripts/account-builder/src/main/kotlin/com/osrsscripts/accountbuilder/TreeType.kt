package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.GatherResource

/**
 * Woodcutting tree types with their level requirements and the exact in-game object names that
 * identify them. Level requirements per the OSRS Wiki. The `members` flag is informational (the UI
 * gates on level; members trees simply are not reachable on a F2P world). A [GatherResource] that
 * matches by object **name** (case-insensitive, exact) — so a yew matches only "Yew" / "Yew tree",
 * never some other object that merely contains the text.
 */
internal enum class TreeType(
    override val displayName: String,
    override val levelReq: Int,
    override val members: Boolean,
    override val objectNames: Set<String>,
) : GatherResource {
    NORMAL("Normal tree", 1, false, setOf("Tree")),
    OAK("Oak", 15, false, setOf("Oak", "Oak tree")),
    WILLOW("Willow", 30, false, setOf("Willow", "Willow tree")),
    TEAK("Teak", 35, true, setOf("Teak", "Teak tree")),
    MAPLE("Maple", 45, false, setOf("Maple", "Maple tree")),
    MAHOGANY("Mahogany", 50, true, setOf("Mahogany", "Mahogany tree")),
    YEW("Yew", 60, false, setOf("Yew", "Yew tree")),
    MAGIC("Magic", 75, true, setOf("Magic", "Magic tree")),
    REDWOOD("Redwood", 90, true, setOf("Redwood", "Redwood tree"));

    override val id: String get() = name
    override val ids: Set<Int> = emptySet()
}
