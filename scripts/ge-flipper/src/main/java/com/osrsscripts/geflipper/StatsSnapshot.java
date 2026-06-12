package com.osrsscripts.geflipper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One tick's worth of live numbers for the sidebar stats display. */
public final class StatsSnapshot {

    private final Duration runtime;
    private final long sessionProfit;
    private final long allTimeProfit;
    private final long flipsCompleted;
    private final long cash;
    private final List<String> offerLines;

    public StatsSnapshot(Duration runtime, long sessionProfit, long allTimeProfit,
                         long flipsCompleted, long cash, List<String> offerLines) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.sessionProfit = sessionProfit;
        this.allTimeProfit = allTimeProfit;
        this.flipsCompleted = flipsCompleted;
        this.cash = cash;
        this.offerLines = Collections.unmodifiableList(new ArrayList<>(offerLines));
    }

    public Duration runtime() {
        return runtime;
    }

    public long sessionProfit() {
        return sessionProfit;
    }

    public long allTimeProfit() {
        return allTimeProfit;
    }

    public long flipsCompleted() {
        return flipsCompleted;
    }

    public long cash() {
        return cash;
    }

    public List<String> offerLines() {
        return offerLines;
    }
}
