package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.TaskSpec

/**
 * A [TaskSpec] plus the SDK-bound step that does one unit of work. The scheduler ranks the pure
 * [TaskSpec] side; the runner calls [execute] on the chosen task. Verified live, not unit-tested.
 */
interface BuilderTask : TaskSpec {
    fun execute()
}
