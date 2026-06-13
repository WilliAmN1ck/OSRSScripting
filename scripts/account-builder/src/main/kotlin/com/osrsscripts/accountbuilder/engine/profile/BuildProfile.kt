package com.osrsscripts.accountbuilder.engine.profile

/** One task's persisted configuration: its [TaskSpec] key plus opaque string params. */
data class TaskConfig(
    val key: String,
    val params: Map<String, String> = emptyMap(),
)

/**
 * A saved account-build plan: the ordered list of task configs, an optional shuffle seed, and a
 * schema version for migration. Pure data — serialized by [ProfileCodec], stored by [ProfileStore].
 */
data class BuildProfile(
    val version: Int = SCHEMA_VERSION,
    val tasks: List<TaskConfig> = emptyList(),
    val shuffleSeed: Long? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
