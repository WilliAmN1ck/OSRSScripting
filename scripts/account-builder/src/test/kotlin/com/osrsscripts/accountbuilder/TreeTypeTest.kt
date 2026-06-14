package com.osrsscripts.accountbuilder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TreeTypeTest {

    @Test
    fun matchesOnlyTheTreesOwnObjectNames() {
        assertTrue(TreeType.NORMAL.matches("Tree", 0))
        assertTrue(TreeType.YEW.matches("Yew", 0))
        assertTrue(TreeType.YEW.matches("Yew tree", 0))
        assertTrue(TreeType.OAK.matches("Oak", 0))
        assertTrue(TreeType.OAK.matches("Oak tree", 0))
    }

    @Test
    fun isCaseInsensitive() {
        assertTrue(TreeType.YEW.matches("yew tree", 0))
        assertTrue(TreeType.NORMAL.matches("tree", 0))
    }

    @Test
    fun doesNotCrossMatchOtherTrees() {
        assertFalse(TreeType.NORMAL.matches("Oak tree", 0)) // "Tree" must not absorb other trees
        assertFalse(TreeType.YEW.matches("Magic tree", 0))
        assertFalse(TreeType.OAK.matches("Tree", 0))
        assertFalse(TreeType.YEW.matches("Yews", 0)) // not a real object, but proves no substring match
    }

    @Test
    fun treesNeverMatchById() {
        // Trees identify by name only; ids is empty, so any id is irrelevant.
        assertFalse(TreeType.YEW.matches("Rocks", 1234))
    }
}
