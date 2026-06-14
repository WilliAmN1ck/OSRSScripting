package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.profile.BuildProfile
import com.osrsscripts.accountbuilder.engine.profile.TaskConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatherConfigPanelTest {

    private fun woodcutting(level: Int) = GatherConfigPanel(
        title = "Trees to cut",
        skillLabel = "Woodcutting",
        resources = TreeType.values().toList(),
        taskKey = "woodcutting",
        resourceParamKey = "trees",
        initialLevel = level,
        defaultSelected = setOf(TreeType.NORMAL.id),
        defaultEnabled = true,
    )

    private fun mining(level: Int) = GatherConfigPanel(
        title = "Rocks to mine",
        skillLabel = "Mining",
        resources = RockType.values().toList(),
        taskKey = "mining",
        resourceParamKey = "rocks",
        initialLevel = level,
        defaultEnabled = false,
    )

    @Test
    fun defaultSelectedResourceIsCheckedAndQualifiedAtLevelOne() {
        assertTrue(TreeType.NORMAL in woodcutting(1).selectedResources())
    }

    @Test
    fun preCheckedHigherResourceActivatesOnceItsLevelIsReached() {
        val panel = woodcutting(1)
        panel.setChecked(TreeType.YEW, true)
        assertFalse(TreeType.YEW in panel.selectedResources()) // level-gated out at 1
        panel.setSkillLevel(60)
        assertTrue(TreeType.YEW in panel.selectedResources()) // activates hands-off at 60
    }

    @Test
    fun uncheckedResourcesAreNeverSelected() {
        assertFalse(TreeType.OAK in woodcutting(99).selectedResources())
    }

    @Test
    fun targetLevelDefaultsToNinetyNine() {
        assertEquals(99, woodcutting(1).targetLevel())
    }

    @Test
    fun applyThenSnapshotPreservesSelectionAndTarget() {
        val profile = BuildProfile(tasks = listOf(TaskConfig("woodcutting", mapOf("trees" to "OAK,YEW", "target" to "60"))))
        val panel = woodcutting(1)
        panel.applyProfile(profile)
        val wc = panel.toProfile().tasks.single { it.key == "woodcutting" }
        assertEquals("60", wc.params["target"])
        assertEquals(setOf("OAK", "YEW"), wc.params["trees"]!!.split(",").toSet())
    }

    @Test
    fun applyingAProfileWithoutThisSkillKeepsDefaults() {
        val panel = woodcutting(1)
        panel.applyProfile(BuildProfile()) // empty first-run default
        assertTrue(TreeType.NORMAL in panel.selectedResources())
    }

    @Test
    fun miningPanelSnapshotsUnderItsOwnKeyAndParam() {
        val mine = mining(99)
        mine.setChecked(RockType.IRON, true)
        val cfg = mine.toProfile().tasks.single { it.key == "mining" }
        assertEquals("IRON", cfg.params["rocks"])
    }

    @Test
    fun miningPanelIsOptInWithNothingSelectedByDefault() {
        assertTrue(mining(99).selectedResources().isEmpty())
    }

    @Test
    fun trainingTogglesDefaultPerSkill() {
        assertTrue(woodcutting(1).isTrainingEnabled()) // Woodcutting on by default
        assertFalse(mining(1).isTrainingEnabled()) // Mining opt-in
    }

    @Test
    fun trainingEnabledRoundTripsThroughTheProfile() {
        val mine = mining(1)
        mine.setTrainingEnabled(true)
        assertEquals("true", mine.toProfile().tasks.single { it.key == "mining" }.params["enabled"])

        val restored = mining(1)
        restored.applyProfile(mine.toProfile())
        assertTrue(restored.isTrainingEnabled())
    }

    @Test
    fun applyingAProfileWithoutThisSkillKeepsTheDefaultEnabledState() {
        val mine = mining(1)
        mine.applyProfile(BuildProfile()) // no mining config
        assertFalse(mine.isTrainingEnabled()) // stays at the opt-in default
    }
}
