package com.osrsscripts.accountbuilder.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderSchedulerTest {

    private fun task(key: String, complete: Boolean = false, valid: Boolean = true) =
        object : TaskSpec {
            override val key = TaskKey(key)
            override val requirements = Requirements()
            override fun isComplete(view: GameView) = complete
            override fun validate(view: GameView) = valid
            override fun progress(view: GameView) = TaskProgress(key)
        }

    private val view = FakeGameView()

    @Test
    fun picksTheFirstRunnableTask() {
        val scheduler = BuilderScheduler(listOf(task("a"), task("b")))
        assertEquals("a", scheduler.next(view)?.key?.value)
    }

    @Test
    fun skipsCompletedTasks() {
        val scheduler = BuilderScheduler(listOf(task("a", complete = true), task("b")))
        assertEquals("b", scheduler.next(view)?.key?.value)
    }

    @Test
    fun skipsTasksThatDoNotValidate() {
        val scheduler = BuilderScheduler(listOf(task("a", valid = false), task("b")))
        assertEquals("b", scheduler.next(view)?.key?.value)
    }

    @Test
    fun returnsNullWhenNothingIsRunnable() {
        val scheduler = BuilderScheduler(listOf(task("a", complete = true), task("b", valid = false)))
        assertNull(scheduler.next(view))
    }

    @Test
    fun allDoneWhenEveryTaskIsCompleteOrNotRunnable() {
        // A pending runnable goal (incomplete + valid) keeps it false.
        assertFalse(BuilderScheduler(listOf(task("a", complete = true), task("b"))).allDone(view))
        // All complete → done.
        assertTrue(BuilderScheduler(listOf(task("a", complete = true))).allDone(view))
        // An incomplete-but-unrunnable task (e.g. nothing selected / opt-in skill) does NOT block done.
        assertTrue(BuilderScheduler(listOf(task("a", complete = true), task("b", valid = false))).allDone(view))
        // But an incomplete + runnable task is still pending.
        assertFalse(BuilderScheduler(listOf(task("a", valid = false), task("b"))).allDone(view))
    }

    @Test
    fun seededShuffleIsDeterministic() {
        val tasks = (1..8).map { task("t$it") }
        val first = BuilderScheduler(tasks, shuffleSeed = 42).tasks.map { it.key.value }
        val second = BuilderScheduler(tasks, shuffleSeed = 42).tasks.map { it.key.value }
        assertEquals(first, second)
    }

    @Test
    fun unshuffledKeepsInputOrder() {
        val tasks = listOf(task("a"), task("b"), task("c"))
        assertEquals(listOf("a", "b", "c"), BuilderScheduler(tasks).tasks.map { it.key.value })
    }
}
