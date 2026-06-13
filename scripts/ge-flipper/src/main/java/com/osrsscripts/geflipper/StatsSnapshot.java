package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.IdleReason;
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
    private final long openBuyCapital;
    private final List<String> offerLines;
    private final List<TradeRow> tradeRows;
    private final int itemsAvoided;
    private final IdleReason idleReason;

    public StatsSnapshot(Duration runtime, long sessionProfit, long allTimeProfit,
                         long flipsCompleted, long cash, long openBuyCapital,
                         List<String> offerLines, List<TradeRow> tradeRows, int itemsAvoided,
                         IdleReason idleReason) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.sessionProfit = sessionProfit;
        this.allTimeProfit = allTimeProfit;
        this.flipsCompleted = flipsCompleted;
        this.cash = cash;
        this.openBuyCapital = openBuyCapital;
        this.offerLines = Collections.unmodifiableList(new ArrayList<>(offerLines));
        this.tradeRows = Collections.unmodifiableList(new ArrayList<>(tradeRows));
        this.itemsAvoided = itemsAvoided;
        this.idleReason = Objects.requireNonNull(idleReason, "idleReason");
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

    /** Gp committed to currently open buy offers — how much of the bankroll is working. */
    public long openBuyCapital() {
        return openBuyCapital;
    }

    public List<String> offerLines() {
        return offerLines;
    }

    public List<TradeRow> tradeRows() {
        return tradeRows;
    }

    /** Number of items the loss-guard is currently excluding from buys. */
    public int itemsAvoided() {
        return itemsAvoided;
    }

    public IdleReason idleReason() {
        return idleReason;
    }

    /** One trade-history table row, best net profit first. */
    public static final class TradeRow {
        private final String itemName;
        private final long netProfit;
        private final int flipsCompleted;
        private final int qtySold;

        public TradeRow(String itemName, long netProfit, int flipsCompleted, int qtySold) {
            this.itemName = Objects.requireNonNull(itemName, "itemName");
            this.netProfit = netProfit;
            this.flipsCompleted = flipsCompleted;
            this.qtySold = qtySold;
        }

        public String itemName() {
            return itemName;
        }

        public long netProfit() {
            return netProfit;
        }

        public int flipsCompleted() {
            return flipsCompleted;
        }

        public int qtySold() {
            return qtySold;
        }
    }
}
