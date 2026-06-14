package com.osrsscripts.accountbuilder.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PicksTest {

    @Test
    fun recognisesPickaxesOfEveryTier() {
        listOf("Bronze pickaxe", "Iron pickaxe", "Steel pickaxe", "Black pickaxe", "Rune pickaxe", "Dragon pickaxe")
            .forEach { assertEquals(true, Picks.isTool(it), it) }
    }

    @Test
    fun ignoresAxesAndNonTools() {
        listOf("Bronze axe", "Rune axe", "Dragon battleaxe", "Logs", "Coins").forEach {
            assertFalse(Picks.isTool(it), it)
        }
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(true, Picks.isTool("rune pickaxe"))
    }

    @Test
    fun bestUsablePicksHighestTierWithinLevel() {
        val bank = listOf("Bronze pickaxe", "Steel pickaxe", "Rune pickaxe", "Coins")
        assertEquals("Rune pickaxe", Picks.bestUsable(bank, 50))
    }

    @Test
    fun bestUsableSkipsPicksAboveLevel() {
        assertEquals("Steel pickaxe", Picks.bestUsable(listOf("Steel pickaxe", "Rune pickaxe"), 40)) // Rune needs 41
    }

    @Test
    fun bestUsableSkipsDegradableAndRarePicks() {
        // Dragon (61) is permanent and preferred; crystal/3rd age/infernal are skipped.
        assertEquals("Dragon pickaxe", Picks.bestUsable(listOf("Crystal pickaxe", "Dragon pickaxe", "3rd age pickaxe"), 99))
        assertNull(Picks.bestUsable(listOf("Crystal pickaxe", "Infernal pickaxe", "Gilded pickaxe"), 99))
    }

    @Test
    fun bestUsableReturnsNullForEmptyBank() {
        assertNull(Picks.bestUsable(emptyList(), 99))
    }
}
