package com.osrsscripts.accountbuilder.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequirementsTest {

    @Test
    fun emptyRequirementsAreAlwaysMet() {
        assertTrue(Requirements().meets(FakeGameView()))
    }

    @Test
    fun skillLevelGate() {
        val req = Requirements(skillLevels = mapOf(Skill.WOODCUTTING to 30))
        assertFalse(req.meets(FakeGameView(levels = mapOf(Skill.WOODCUTTING to 29))))
        assertTrue(req.meets(FakeGameView(levels = mapOf(Skill.WOODCUTTING to 30))))
        assertTrue(req.meets(FakeGameView(levels = mapOf(Skill.WOODCUTTING to 60))))
    }

    @Test
    fun itemGate() {
        val req = Requirements(items = listOf("Bronze axe"))
        assertFalse(req.meets(FakeGameView()))
        assertTrue(req.meets(FakeGameView(items = setOf("Bronze axe"))))
    }

    @Test
    fun questGate() {
        val req = Requirements(questsComplete = listOf("Dragon Slayer I"))
        assertFalse(req.meets(FakeGameView()))
        assertTrue(req.meets(FakeGameView(completedQuests = setOf("Dragon Slayer I"))))
    }

    @Test
    fun membersGate() {
        val req = Requirements(members = true)
        assertFalse(req.meets(FakeGameView(membersWorld = false)))
        assertTrue(req.meets(FakeGameView(membersWorld = true)))
    }

    @Test
    fun everyConditionMustHold() {
        val req = Requirements(
            skillLevels = mapOf(Skill.WOODCUTTING to 15),
            items = listOf("Bronze axe"),
        )
        assertFalse(req.meets(FakeGameView(levels = mapOf(Skill.WOODCUTTING to 15)))) // axe missing
        assertTrue(
            req.meets(
                FakeGameView(levels = mapOf(Skill.WOODCUTTING to 15), items = setOf("Bronze axe")),
            ),
        )
    }
}
