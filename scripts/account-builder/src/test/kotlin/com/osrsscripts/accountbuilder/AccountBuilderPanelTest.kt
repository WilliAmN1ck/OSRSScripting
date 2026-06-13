package com.osrsscripts.accountbuilder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountBuilderPanelTest {

    @Test
    fun normalTreeIsCheckedAndQualifiedAtLevelOne() {
        val panel = AccountBuilderPanel(1)
        assertTrue(TreeType.NORMAL in panel.selectedTrees())
    }

    @Test
    fun preCheckedHigherTreeActivatesOnceItsLevelIsReached() {
        val panel = AccountBuilderPanel(1)
        panel.setChecked(TreeType.YEW, true)
        // Selected but not yet qualified at level 1 — level-gated out.
        assertFalse(TreeType.YEW in panel.selectedTrees())
        // Reaching the requirement activates it with no further interaction (hands-off progression).
        panel.setWoodcuttingLevel(60)
        assertTrue(TreeType.YEW in panel.selectedTrees())
    }

    @Test
    fun uncheckedTreesAreNeverSelected() {
        val panel = AccountBuilderPanel(99) // qualifies for everything, but only NORMAL is checked
        assertFalse(TreeType.OAK in panel.selectedTrees())
    }

    @Test
    fun targetLevelDefaultsToNinetyNine() {
        assertEquals(99, AccountBuilderPanel(1).targetLevel())
    }
}
