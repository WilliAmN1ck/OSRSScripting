package com.osrsscripts.accountbuilder.engine.profile

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads and writes a [BuildProfile] as JSON at [path] (e.g. a file in the script-settings directory).
 * A missing file loads as an empty default profile, so a first run needs no setup. Pure file I/O —
 * unit-testable with a temp directory.
 */
class ProfileStore(private val path: Path) {

    fun save(profile: BuildProfile) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, ProfileCodec.toJson(profile))
    }

    fun load(): BuildProfile =
        try {
            if (Files.exists(path)) ProfileCodec.fromJson(Files.readString(path)) else BuildProfile()
        } catch (e: Exception) {
            // Unreadable or corrupt file: start from a default profile rather than crash the run.
            BuildProfile()
        }
}
