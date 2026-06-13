package com.osrsscripts.accountbuilder

/**
 * Woodcutting tree types with their level requirements and how to recognise their in-game objects.
 * Level requirements per the OSRS Wiki. The `members` flag is informational (the UI gates on level;
 * members trees simply are not reachable on a F2P world).
 */
internal enum class TreeType(
    val displayName: String,
    val levelReq: Int,
    val members: Boolean,
    private val keyword: String,
    private val exact: Boolean = false,
) {
    NORMAL("Normal tree", 1, false, "Tree", exact = true),
    OAK("Oak", 15, false, "Oak"),
    WILLOW("Willow", 30, false, "Willow"),
    TEAK("Teak", 35, true, "Teak"),
    MAPLE("Maple", 45, false, "Maple"),
    MAHOGANY("Mahogany", 50, true, "Mahogany"),
    YEW("Yew", 60, false, "Yew"),
    MAGIC("Magic", 75, true, "Magic"),
    REDWOOD("Redwood", 90, true, "Redwood");

    /**
     * Whether a game object's name identifies this tree. The normal tree matches its name exactly
     * ("Tree"); the rest match by keyword so naming variants ("Oak" / "Oak tree") are all covered —
     * and the exact normal match prevents "Oak tree" from being read as a normal tree.
     */
    fun matches(objectName: String): Boolean =
        if (exact) objectName == keyword else objectName.contains(keyword, ignoreCase = true)
}
