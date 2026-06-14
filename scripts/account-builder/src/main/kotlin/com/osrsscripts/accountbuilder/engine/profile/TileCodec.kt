package com.osrsscripts.accountbuilder.engine.profile

/**
 * (De)serializes a world tile to/from the `"x,y,plane"` string stored in a [TaskConfig] param. Pure
 * (no SDK types) so it is unit-testable; the SDK `WorldTile` bridge lives at the script edge.
 */
object TileCodec {

    /** Formats coordinates as the persisted `"x,y,plane"` string. */
    fun format(x: Int, y: Int, plane: Int): String = "$x,$y,$plane"

    /** Parses `"x,y,plane"` back to (x, y, plane), or null if the string is missing/malformed. */
    fun parse(value: String?): Triple<Int, Int, Int>? {
        val parts = value?.split(",") ?: return null
        if (parts.size != 3) return null
        val (x, y, plane) = parts.map { it.trim().toIntOrNull() ?: return null }
        return Triple(x, y, plane)
    }
}
