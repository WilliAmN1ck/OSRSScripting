package com.osrsscripts.accountbuilder.engine

/**
 * Stall detector for unattended runs: fed a monotonic progress signal (e.g. total XP) and the
 * current time each tick, it returns STOP when the signal has not advanced for [stallLimitMs].
 * Deliberately thin — a single no-progress window — until live soak data justifies a richer policy.
 */
class Watchdog(private val stallLimitMs: Long) {

    enum class Decision { CONTINUE, STOP }

    private var lastSignal: Long? = null
    private var lastChangeAtMs: Long = 0L

    fun evaluate(nowMs: Long, progressSignal: Long): Decision {
        if (lastSignal == null || progressSignal != lastSignal) {
            lastSignal = progressSignal
            lastChangeAtMs = nowMs
            return Decision.CONTINUE
        }
        return if (nowMs - lastChangeAtMs >= stallLimitMs) Decision.STOP else Decision.CONTINUE
    }
}
