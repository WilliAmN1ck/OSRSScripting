package com.osrsscripts.accountbuilder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RockTypeTest {

    @Test
    fun matchesItsOreRockByName() {
        // Mineable rocks are named per ore ("Copper rocks", …) — matched like trees.
        assertTrue(RockType.COPPER.matches("Copper rocks", 0))
        assertTrue(RockType.ADAMANTITE.matches("Adamantite rocks", 0))
    }

    @Test
    fun isCaseInsensitive() {
        assertTrue(RockType.IRON.matches("iron rocks", 0))
    }

    @Test
    fun doesNotCrossMatchOtherOresOrDepletedRocks() {
        assertFalse(RockType.COPPER.matches("Tin rocks", 0))
        assertFalse(RockType.COAL.matches("Rocks", 0)) // a depleted/generic rock is not a specific ore
    }

    @Test
    fun oreLadderLevelRequirements() {
        assertEquals(1, RockType.COPPER.levelReq)
        assertEquals(1, RockType.TIN.levelReq)
        assertEquals(15, RockType.IRON.levelReq)
        assertEquals(20, RockType.SILVER.levelReq)
        assertEquals(30, RockType.COAL.levelReq)
        assertEquals(40, RockType.GOLD.levelReq)
        assertEquals(55, RockType.MITHRIL.levelReq)
        assertEquals(70, RockType.ADAMANTITE.levelReq)
    }

    @Test
    fun everyOreIsFreeToPlayAndHasAName() {
        RockType.values().forEach {
            assertFalse(it.members, it.displayName)
            assertTrue(it.objectNames.isNotEmpty(), it.displayName)
        }
    }

    @Test
    fun idIsTheEnumName() {
        assertEquals("ADAMANTITE", RockType.ADAMANTITE.id)
    }
}
