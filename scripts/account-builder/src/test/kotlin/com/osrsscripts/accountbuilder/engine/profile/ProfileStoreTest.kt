package com.osrsscripts.accountbuilder.engine.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ProfileStoreTest {

    @Test
    fun savesAndLoadsRoundTrip(@TempDir dir: Path) {
        val store = ProfileStore(dir.resolve("profile.json"))
        val profile = BuildProfile(tasks = listOf(TaskConfig("woodcutting", mapOf("target" to "30"))))
        store.save(profile)
        assertEquals(profile, store.load())
    }

    @Test
    fun loadsDefaultWhenFileMissing(@TempDir dir: Path) {
        val store = ProfileStore(dir.resolve("does-not-exist.json"))
        assertEquals(BuildProfile(), store.load())
    }

    @Test
    fun loadsDefaultWhenFileCorrupt(@TempDir dir: Path) {
        val file = dir.resolve("profile.json")
        Files.writeString(file, "{ this is not valid json")
        assertEquals(BuildProfile(), ProfileStore(file).load())
    }

    @Test
    fun createsParentDirectories(@TempDir dir: Path) {
        val store = ProfileStore(dir.resolve("nested/sub/profile.json"))
        store.save(BuildProfile(shuffleSeed = 7L))
        assertEquals(BuildProfile(shuffleSeed = 7L), store.load())
    }
}
