package com.osrsscripts.accountbuilder.engine.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileCodecTest {

    @Test
    fun roundTripsAFullProfile() {
        val profile = BuildProfile(
            tasks = listOf(
                TaskConfig("woodcutting", mapOf("trees" to "NORMAL,OAK", "target" to "60")),
                TaskConfig("fishing"),
            ),
            shuffleSeed = 42L,
        )
        assertEquals(profile, ProfileCodec.fromJson(ProfileCodec.toJson(profile)))
    }

    @Test
    fun emptyObjectDegradesToDefaults() {
        val profile = ProfileCodec.fromJson("{}")
        assertEquals(BuildProfile.SCHEMA_VERSION, profile.version)
        assertEquals(emptyList<TaskConfig>(), profile.tasks)
        assertNull(profile.shuffleSeed)
    }

    @Test
    fun missingVersionNormalizesToCurrent() {
        val profile = ProfileCodec.fromJson("""{"tasks":[{"key":"woodcutting"}]}""")
        assertEquals(BuildProfile.SCHEMA_VERSION, profile.version)
        assertEquals(1, profile.tasks.size)
        assertEquals("woodcutting", profile.tasks[0].key)
        assertEquals(emptyMap<String, String>(), profile.tasks[0].params)
    }
}
