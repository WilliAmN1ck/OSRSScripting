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

/**
 * Returns a copy with [paramKey] set to [value] on the task with key [taskKey] — or removed when
 * [value] is null. Adds the task config if it isn't present yet. Other tasks and params are untouched.
 */
fun BuildProfile.withTaskParam(taskKey: String, paramKey: String, value: String?): BuildProfile {
    fun TaskConfig.applied() = copy(
        params = params.toMutableMap().apply { if (value == null) remove(paramKey) else put(paramKey, value) },
    )
    val updated = if (tasks.any { it.key == taskKey }) {
        tasks.map { if (it.key == taskKey) it.applied() else it }
    } else if (value != null) {
        tasks + TaskConfig(taskKey, mapOf(paramKey to value))
    } else {
        tasks
    }
    return copy(tasks = updated)
}
