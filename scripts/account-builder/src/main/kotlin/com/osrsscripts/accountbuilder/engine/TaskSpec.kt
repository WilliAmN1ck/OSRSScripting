package com.osrsscripts.accountbuilder.engine

/** Stable identity of a task, used by config references and persistence. */
@JvmInline
value class TaskKey(val value: String)

/** A human-readable progress readout for a task, for paint/UI. */
data class TaskProgress(val label: String, val percent: Double? = null)

/**
 * The pure, SDK-free contract the [BuilderScheduler] ranks. [isComplete] lets the scheduler skip a
 * finished task; [validate] gates a task on its requirements (plus any situational check). The
 * SDK-bound step lives in BuilderTask (task/), which extends this with an execute() that does one
 * unit of work.
 */
interface TaskSpec {
    val key: TaskKey
    val requirements: Requirements

    /** Goal reached — the scheduler skips this task. */
    fun isComplete(view: GameView): Boolean

    /** Whether the task can run right now. Defaults to its [requirements] being met. */
    fun validate(view: GameView): Boolean = requirements.meets(view)

    /** Current progress, for display. */
    fun progress(view: GameView): TaskProgress
}
