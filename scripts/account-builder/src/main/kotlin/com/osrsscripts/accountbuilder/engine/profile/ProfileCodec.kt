package com.osrsscripts.accountbuilder.engine.profile

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * JSON (de)serialization for [BuildProfile] via gson. Reading normalizes the result so a missing or
 * older file degrades to sensible defaults rather than nulls — gson populates fields by reflection
 * and does not honour Kotlin defaults, so we re-apply them here.
 */
object ProfileCodec {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(profile: BuildProfile): String = gson.toJson(profile)

    fun fromJson(json: String): BuildProfile {
        val raw = gson.fromJson(json, BuildProfile::class.java) ?: return BuildProfile()
        @Suppress("USELESS_ELVIS") // gson can leave non-null fields null when absent from the JSON
        val tasks = (raw.tasks ?: emptyList()).map {
            TaskConfig(key = it.key, params = it.params ?: emptyMap())
        }
        val version = if (raw.version <= 0) BuildProfile.SCHEMA_VERSION else raw.version
        return BuildProfile(version = version, tasks = tasks, shuffleSeed = raw.shuffleSeed)
    }
}
