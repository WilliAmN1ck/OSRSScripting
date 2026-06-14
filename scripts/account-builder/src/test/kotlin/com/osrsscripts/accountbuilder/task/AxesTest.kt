package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.FakeGameView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AxesTest {

    @Test
    fun keepsWoodcuttingAxesOfEveryTier() {
        listOf(
            "Bronze axe", "Iron axe", "Rune axe", "Dragon axe",
            "Crystal axe", "Infernal axe", "3rd age axe", "Gilded axe",
            "Dragon felling axe",
        ).forEach { assertTrue(Axes.isTool(it), it) }
    }

    @Test
    fun ignoresPickaxesAndAxeWeapons() {
        listOf("Bronze pickaxe", "Rune pickaxe", "Dragon battleaxe").forEach {
            assertFalse(Axes.isTool(it), it)
        }
    }

    @Test
    fun ignoresThrowingAxes() {
        listOf("Bronze thrownaxe", "Rune thrownaxe", "Dragon thrownaxe").forEach {
            assertFalse(Axes.isTool(it), it)
        }
    }

    @Test
    fun ignoresNonAxes() {
        listOf("Logs", "Oak logs", "Bird nest", "Coins").forEach {
            assertFalse(Axes.isTool(it), it)
        }
    }

    @Test
    fun isCaseInsensitive() {
        assertTrue(Axes.isTool("bronze axe"))
        assertFalse(Axes.isTool("BRONZE PICKAXE"))
    }

    @Test
    fun hasAxeWhenOneIsInInventory() {
        assertTrue(Axes.hasTool(FakeGameView(items = setOf("Logs", "Bronze axe"))))
    }

    @Test
    fun hasAxeWhenOneIsEquipped() {
        assertTrue(Axes.hasTool(FakeGameView(equipped = setOf("Rune axe"))))
    }

    @Test
    fun noAxeWhenNeitherInventoryNorEquipmentHasOne() {
        assertFalse(Axes.hasTool(FakeGameView(items = setOf("Logs", "Bronze pickaxe"), equipped = setOf("Amulet of power"))))
    }

    @Test
    fun noAxeWhenInventoryAndEquipmentAreEmpty() {
        assertFalse(Axes.hasTool(FakeGameView()))
    }

    @Test
    fun bestUsableAxePicksHighestTierWithinLevel() {
        val bank = listOf("Bronze axe", "Steel axe", "Rune axe", "Logs", "Coins")
        assertEquals("Rune axe", Axes.bestUsable(bank, 50))
    }

    @Test
    fun bestUsableAxeSkipsAxesAboveLevel() {
        assertEquals("Bronze axe", Axes.bestUsable(listOf("Bronze axe", "Rune axe"), 40))
    }

    @Test
    fun bestUsableAxeRecognisesFellingAxes() {
        assertEquals(
            "Rune felling axe",
            Axes.bestUsable(listOf("Steel felling axe", "Rune felling axe"), 41),
        )
    }

    @Test
    fun bestUsableAxeReturnsNullWhenNoneUsable() {
        assertNull(Axes.bestUsable(listOf("Dragon axe", "Logs"), 40)) // Dragon needs 61
    }

    @Test
    fun bestUsableAxeIgnoresNonAxesPickaxesAndThrowingAxes() {
        assertNull(Axes.bestUsable(listOf("Rune pickaxe", "Bronze thrownaxe", "Coins"), 99))
    }

    @Test
    fun bestUsableAxeReturnsNullForEmptyBank() {
        assertNull(Axes.bestUsable(emptyList(), 99))
    }

    @Test
    fun bestUsableAxeSkipsDegradableAndRareAxesInFavourOfAPlainOne() {
        // Crystal degrades; a plain Dragon axe is the right pick even though Crystal's req is higher.
        assertEquals("Dragon axe", Axes.bestUsable(listOf("Crystal axe", "Dragon axe"), 99))
        // Gilded is a rare cosmetic — prefer the plain Rune axe of the same tier.
        assertEquals("Rune axe", Axes.bestUsable(listOf("Gilded axe", "Rune axe"), 50))
    }

    @Test
    fun bestUsableAxeReturnsNullWhenBankOnlyHasDegradableOrRareAxes() {
        val bank = listOf("Crystal axe", "Infernal axe", "3rd age axe", "Gilded axe")
        assertNull(Axes.bestUsable(bank, 99))
    }
}
