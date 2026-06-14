package com.osrsscripts.accountbuilder.engine

/**
 * A gatherable resource — a tree, a rock, etc. Kept pure (no SDK types) so concrete resource types stay
 * unit-testable. Matching is data-driven: trees identify by object **name** (case-insensitive), rocks by
 * object **id** (they are all named "Rocks"). The SDK-bound gatherer calls [matches] with the object's
 * name and id.
 */
interface GatherResource {
    /** Stable persistence key — the enum constant name. */
    val id: String
    val displayName: String
    val levelReq: Int
    val members: Boolean
    val objectNames: Set<String>
    val ids: Set<Int>

    fun matches(objectName: String, objectId: Int): Boolean =
        objectNames.any { it.equals(objectName, ignoreCase = true) } || objectId in ids
}
