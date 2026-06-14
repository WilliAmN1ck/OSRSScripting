package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.GatherResource

/**
 * F2P mineable ore types with their Mining level requirements (per the OSRS Wiki). A [GatherResource]
 * that matches by object **id**: every mineable rock is named "Rocks", so the ore is identified by the
 * game object's id, which varies by mine — hence an id-set per ore ([objectNames] is empty).
 *
 * IMPORTANT: the [ids] below are **PROVISIONAL** best-effort values from web research and MUST be
 * confirmed/extended in-game — Phase 5 sub-phase D logs the reachable mineable rock ids at each covered
 * F2P mine (Al-Kharid primary) and this table is corrected from that capture. Until then, Mining may not
 * locate rocks at every mine.
 */
internal enum class RockType(
    override val displayName: String,
    override val levelReq: Int,
    override val ids: Set<Int>,
) : GatherResource {
    COPPER("Copper", 1, setOf(2090, 2091)),
    TIN("Tin", 1, setOf(2094, 2095)),
    IRON("Iron", 15, setOf(2092, 2093)),
    SILVER("Silver", 20, setOf(2100, 2101)),
    COAL("Coal", 30, setOf(2096, 2097)),
    GOLD("Gold", 40, setOf(2098, 2099)),
    MITHRIL("Mithril", 55, setOf(2102, 2103)),
    ADAMANTITE("Adamantite", 70, setOf(2104, 2105));

    override val id: String get() = name
    override val members: Boolean = false
    override val objectNames: Set<String> = emptySet()
}
