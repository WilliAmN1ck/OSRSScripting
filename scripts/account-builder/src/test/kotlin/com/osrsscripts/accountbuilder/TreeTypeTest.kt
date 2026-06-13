package com.osrsscripts.accountbuilder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TreeTypeTest {

    @Test
    fun matchesOnlyTheTreesOwnObjectNames() {
        assertTrue(TreeType.NORMAL.matches("Tree"))
        assertTrue(TreeType.YEW.matches("Yew"))
        assertTrue(TreeType.YEW.matches("Yew tree"))
        assertTrue(TreeType.OAK.matches("Oak"))
        assertTrue(TreeType.OAK.matches("Oak tree"))
    }

    @Test
    fun isCaseInsensitive() {
        assertTrue(TreeType.YEW.matches("yew tree"))
        assertTrue(TreeType.NORMAL.matches("tree"))
    }

    @Test
    fun doesNotCrossMatchOtherTrees() {
        assertFalse(TreeType.NORMAL.matches("Oak tree")) // "Tree" must not absorb other trees
        assertFalse(TreeType.YEW.matches("Magic tree"))
        assertFalse(TreeType.OAK.matches("Tree"))
        assertFalse(TreeType.YEW.matches("Yews")) // not a real object, but proves no substring match
    }
}
