package com.osrsscripts.accountbuilder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RockTypeTest {

    @Test
    fun matchesByIdRegardlessOfName() {
        val copperId = RockType.COPPER.ids.first()
        // Every rock is named "Rocks"; identity is the id, so the name is irrelevant.
        assertTrue(RockType.COPPER.matches("Rocks", copperId))
        assertTrue(RockType.COPPER.matches("anything at all", copperId))
    }

    @Test
    fun doesNotMatchAnUnknownId() {
        assertFalse(RockType.COPPER.matches("Rocks", -1))
    }

    @Test
    fun rocksNeverMatchByName() {
        // objectNames is empty for rocks, so a name alone never matches.
        assertFalse(RockType.COAL.matches("Coal", 0))
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
    fun everyOreIsFreeToPlayAndHasAtLeastOneId() {
        RockType.values().forEach {
            assertFalse(it.members, it.displayName)
            assertTrue(it.ids.isNotEmpty(), it.displayName)
        }
    }

    @Test
    fun idIsTheEnumName() {
        assertEquals("ADAMANTITE", RockType.ADAMANTITE.id)
    }
}
