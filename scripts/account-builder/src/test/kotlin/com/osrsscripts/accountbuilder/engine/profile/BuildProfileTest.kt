package com.osrsscripts.accountbuilder.engine.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuildProfileTest {

    @Test
    fun setsParamOnAnExistingTask() {
        val profile = BuildProfile(tasks = listOf(TaskConfig("woodcutting", mapOf("trees" to "OAK"))))
        val updated = profile.withTaskParam("woodcutting", "chopTile", "1,2,0")
        val wc = updated.tasks.single { it.key == "woodcutting" }
        assertEquals("1,2,0", wc.params["chopTile"])
        assertEquals("OAK", wc.params["trees"]) // existing param preserved
    }

    @Test
    fun removesParamWhenValueIsNull() {
        val profile = BuildProfile(tasks = listOf(TaskConfig("woodcutting", mapOf("trees" to "OAK", "chopTile" to "1,2,0"))))
        val updated = profile.withTaskParam("woodcutting", "chopTile", null)
        val wc = updated.tasks.single { it.key == "woodcutting" }
        assertNull(wc.params["chopTile"])
        assertEquals("OAK", wc.params["trees"])
    }

    @Test
    fun addsTaskConfigWhenAbsent() {
        val updated = BuildProfile().withTaskParam("woodcutting", "chopTile", "1,2,0")
        assertEquals("1,2,0", updated.tasks.single { it.key == "woodcutting" }.params["chopTile"])
    }

    @Test
    fun leavesOtherTasksUntouched() {
        val profile = BuildProfile(
            tasks = listOf(
                TaskConfig("woodcutting", mapOf("trees" to "OAK")),
                TaskConfig("mining", mapOf("rocks" to "TIN")),
            ),
        )
        val updated = profile.withTaskParam("woodcutting", "chopTile", "1,2,0")
        assertEquals(mapOf("rocks" to "TIN"), updated.tasks.single { it.key == "mining" }.params)
    }

    @Test
    fun removingAnAbsentParamIsANoOp() {
        val profile = BuildProfile(tasks = listOf(TaskConfig("woodcutting", mapOf("trees" to "OAK"))))
        val updated = profile.withTaskParam("woodcutting", "chopTile", null)
        assertFalse(updated.tasks.single { it.key == "woodcutting" }.params.containsKey("chopTile"))
    }

    @Test
    fun preservesVersionAndShuffleSeed() {
        val profile = BuildProfile(version = 1, tasks = listOf(TaskConfig("woodcutting")), shuffleSeed = 42L)
        val updated = profile.withTaskParam("woodcutting", "chopTile", "1,2,0")
        assertEquals(1, updated.version)
        assertEquals(42L, updated.shuffleSeed)
    }

    @Test
    fun getTaskParamReadsBackWhatWithTaskParamWrote() {
        val profile = BuildProfile().withTaskParam("woodcutting", "chopTile", "1,2,0")
        assertEquals("1,2,0", profile.getTaskParam("woodcutting", "chopTile"))
    }

    @Test
    fun getTaskParamReturnsNullForAbsentTaskOrParam() {
        val profile = BuildProfile(tasks = listOf(TaskConfig("woodcutting", mapOf("trees" to "OAK"))))
        assertNull(profile.getTaskParam("woodcutting", "chopTile")) // task present, param absent
        assertNull(profile.getTaskParam("mining", "chopTile")) // task absent
    }
}
