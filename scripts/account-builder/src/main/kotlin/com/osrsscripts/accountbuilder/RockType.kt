package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.GatherResource

/**
 * F2P mineable ore types with their Mining level requirements (per the OSRS Wiki). A [GatherResource]
 * that matches by object **name** — confirmed in-game, mineable rocks are named per ore ("Copper rocks",
 * "Tin rocks", …), not a generic "Rocks", so (exactly like trees) the name identifies the ore and
 * matching is robust at every mine with no per-mine id table. Exact, case-insensitive name match — a
 * depleted/generic "Rocks" never matches a specific ore.
 */
internal enum class RockType(
    override val displayName: String,
    override val levelReq: Int,
    override val objectNames: Set<String>,
) : GatherResource {
    COPPER("Copper", 1, setOf("Copper rocks")),
    TIN("Tin", 1, setOf("Tin rocks")),
    IRON("Iron", 15, setOf("Iron rocks")),
    SILVER("Silver", 20, setOf("Silver rocks")),
    COAL("Coal", 30, setOf("Coal rocks")),
    GOLD("Gold", 40, setOf("Gold rocks")),
    MITHRIL("Mithril", 55, setOf("Mithril rocks")),
    ADAMANTITE("Adamantite", 70, setOf("Adamantite rocks"));

    override val id: String get() = name
    override val members: Boolean = false
    override val ids: Set<Int> = emptySet()
}
