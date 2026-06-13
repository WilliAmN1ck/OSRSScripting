package com.osrsscripts.accountbuilder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TreeTypeTest {

    @Test
    fun normalMatchesOnlyTheExactTreeName() {
        assertTrue(TreeType.NORMAL.matches("Tree"))
        assertFalse(TreeType.NORMAL.matches("Oak tree"))
        assertFalse(TreeType.NORMAL.matches("Willow"))
    }

    @Test
    fun keywordTreesMatchTheirNameVariants() {
        assertTrue(TreeType.OAK.matches("Oak"))
        assertTrue(TreeType.OAK.matches("Oak tree"))
        assertTrue(TreeType.YEW.matches("Yew"))
        assertTrue(TreeType.MAGIC.matches("Magic tree"))
    }

    @Test
    fun keywordTreesDoNotMatchOtherTrees() {
        assertFalse(TreeType.OAK.matches("Tree"))
        assertFalse(TreeType.WILLOW.matches("Yew"))
        assertFalse(TreeType.YEW.matches("Magic tree"))
    }
}
