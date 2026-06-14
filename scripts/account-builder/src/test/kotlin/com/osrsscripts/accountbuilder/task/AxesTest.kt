package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.FakeGameView
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AxesTest {

    @Test
    fun keepsWoodcuttingAxesOfEveryTier() {
        listOf(
            "Bronze axe", "Iron axe", "Rune axe", "Dragon axe",
            "Crystal axe", "Infernal axe", "3rd age axe", "Gilded axe",
            "Dragon felling axe",
        ).forEach { assertTrue(Axes.isAxe(it), it) }
    }

    @Test
    fun ignoresPickaxesAndAxeWeapons() {
        listOf("Bronze pickaxe", "Rune pickaxe", "Dragon battleaxe").forEach {
            assertFalse(Axes.isAxe(it), it)
        }
    }

    @Test
    fun ignoresThrowingAxes() {
        listOf("Bronze thrownaxe", "Rune thrownaxe", "Dragon thrownaxe").forEach {
            assertFalse(Axes.isAxe(it), it)
        }
    }

    @Test
    fun ignoresNonAxes() {
        listOf("Logs", "Oak logs", "Bird nest", "Coins").forEach {
            assertFalse(Axes.isAxe(it), it)
        }
    }

    @Test
    fun isCaseInsensitive() {
        assertTrue(Axes.isAxe("bronze axe"))
        assertFalse(Axes.isAxe("BRONZE PICKAXE"))
    }

    @Test
    fun hasAxeWhenOneIsInInventory() {
        assertTrue(Axes.hasAxe(FakeGameView(items = setOf("Logs", "Bronze axe"))))
    }

    @Test
    fun hasAxeWhenOneIsEquipped() {
        assertTrue(Axes.hasAxe(FakeGameView(equipped = setOf("Rune axe"))))
    }

    @Test
    fun noAxeWhenNeitherInventoryNorEquipmentHasOne() {
        assertFalse(Axes.hasAxe(FakeGameView(items = setOf("Logs", "Bronze pickaxe"), equipped = setOf("Amulet of power"))))
    }

    @Test
    fun noAxeWhenInventoryAndEquipmentAreEmpty() {
        assertFalse(Axes.hasAxe(FakeGameView()))
    }
}
