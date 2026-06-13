package com.osrsscripts.accountbuilder.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WatchdogTest {

    @Test
    fun continuesWhileProgressAdvances() {
        val watchdog = Watchdog(stallLimitMs = 1000)
        assertEquals(Watchdog.Decision.CONTINUE, watchdog.evaluate(0, 100))
        assertEquals(Watchdog.Decision.CONTINUE, watchdog.evaluate(2000, 150)) // advanced despite gap
    }

    @Test
    fun stopsAfterTheStallLimit() {
        val watchdog = Watchdog(stallLimitMs = 1000)
        watchdog.evaluate(0, 100)
        assertEquals(Watchdog.Decision.CONTINUE, watchdog.evaluate(999, 100))
        assertEquals(Watchdog.Decision.STOP, watchdog.evaluate(1000, 100))
    }

    @Test
    fun progressResetsTheStallTimer() {
        val watchdog = Watchdog(stallLimitMs = 1000)
        watchdog.evaluate(0, 100)
        watchdog.evaluate(900, 100) // still stalling
        assertEquals(Watchdog.Decision.CONTINUE, watchdog.evaluate(950, 200)) // progress resets
        assertEquals(Watchdog.Decision.CONTINUE, watchdog.evaluate(1900, 200)) // 950ms < limit
    }
}
