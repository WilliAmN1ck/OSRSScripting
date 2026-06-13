package com.osrsscripts.core.persistence;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.ge.TradeHistory;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.OfferSide;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the runtime flipper state ({@link BuyLimitLedger}, {@link StockLedger},
 * {@link OfferTracker}) and its persistable form ({@link PersistedState}).
 */
public final class StateMapper {

    private StateMapper() {
    }

    public static PersistedState snapshot(BuyLimitLedger buyLimits, StockLedger stock,
                                          OfferTracker tracker, TradeHistory history,
                                          FlipConfig config) {
        List<LedgerEntry> ledgerEntries = new ArrayList<>();
        for (BuyLimitLedger.Purchase p : buyLimits.purchases()) {
            ledgerEntries.add(new LedgerEntry(p.itemId(), p.qty(), p.at().toEpochMilli()));
        }
        List<StockEntry> stockEntries = new ArrayList<>();
        for (StockLedger.Lot lot : stock.lots()) {
            stockEntries.add(new StockEntry(lot.itemId(), lot.qty(), lot.pricePerItem()));
        }
        List<OfferStampEntry> stampEntries = new ArrayList<>();
        for (OfferTracker.Stamp s : tracker.stamps()) {
            stampEntries.add(new OfferStampEntry(s.slot(), s.itemId(), s.side().name(),
                    s.pricePerItem(), s.filled(), s.transferredGold(),
                    s.placedAt().toEpochMilli()));
        }
        List<TradeRecordEntry> tradeEntries = new ArrayList<>();
        for (TradeHistory.ItemRecord r : history.records()) {
            tradeEntries.add(new TradeRecordEntry(r.itemId(), r.netProfit(), r.flipsCompleted(),
                    r.qtySold(), r.lastTradedEpochMillis()));
        }
        PersistedConfig persistedConfig = new PersistedConfig(config.capitalCap(),
                config.perItemCapitalCap(), config.minMarginGp(), config.minMarginPct(),
                config.minVolume(), config.maxSlots(), config.maxOfferAgeBuy().toMinutes(),
                config.maxOfferAgeSell().toMinutes(), null, config.membersItemsAllowed(),
                config.minDeploymentGp(), config.sellExitAfterRelists(), config.avoidAfterLossGp());
        return new PersistedState(ledgerEntries, stockEntries, stampEntries, tradeEntries,
                persistedConfig, tracker.realizedProfit(), tracker.flipsCompleted());
    }

    /** The persisted run configuration, or {@code null} when the state predates it. */
    public static FlipConfig restoredConfig(PersistedState state) {
        PersistedConfig c = state.config();
        if (c == null) {
            return null;
        }
        return FlipConfig.builder()
                .capitalCap(c.capitalCap())
                .perItemCapitalCap(c.perItemCapitalCap())
                .minMarginGp(c.minMarginGp())
                .minMarginPct(c.minMarginPct())
                .minVolume(c.minVolume())
                .maxSlots(c.maxSlots())
                .maxOfferAgeBuy(Duration.ofMinutes(c.maxOfferAgeBuyMinutes()))
                .maxOfferAgeSell(Duration.ofMinutes(c.maxOfferAgeSellMinutes()))
                .membersItemsAllowed(c.membersItemsAllowed())
                .minDeploymentGp(c.minDeploymentGp())
                .sellExitAfterRelists(c.sellExitAfterRelists())
                .avoidAfterLossGp(c.avoidAfterLossGp())
                .build();
    }

    public static void restore(PersistedState state, BuyLimitLedger buyLimits, StockLedger stock,
                               OfferTracker tracker, TradeHistory history) {
        List<BuyLimitLedger.Purchase> purchases = new ArrayList<>();
        for (LedgerEntry e : state.ledgerEntries()) {
            purchases.add(new BuyLimitLedger.Purchase(e.itemId(), e.qty(),
                    Instant.ofEpochMilli(e.epochMillis())));
        }
        buyLimits.load(purchases);

        List<StockLedger.Lot> lots = new ArrayList<>();
        for (StockEntry e : state.stockEntries()) {
            lots.add(new StockLedger.Lot(e.itemId(), e.qty(), e.pricePerItem()));
        }
        stock.load(lots);

        List<OfferTracker.Stamp> stamps = new ArrayList<>();
        for (OfferStampEntry e : state.offerStamps()) {
            OfferSide side;
            try {
                side = OfferSide.valueOf(e.side());
            } catch (IllegalArgumentException | NullPointerException invalid) {
                continue; // fail safe: a stamp we cannot read is just re-stamped first-seen
            }
            // Files written before gold tracking carry no baseline; approximate it from the
            // listed price so the first observe after upgrade does not re-count old fills.
            long gold = e.transferredGold() == 0L && e.filled() > 0
                    ? e.pricePerItem() * e.filled()
                    : e.transferredGold();
            stamps.add(new OfferTracker.Stamp(e.slot(), e.itemId(), side, e.pricePerItem(),
                    e.filled(), gold, Instant.ofEpochMilli(e.placedAtEpochMillis())));
        }
        tracker.restore(stamps, state.realizedProfit(), state.flipsCompleted());

        List<TradeHistory.ItemRecord> tradeRecords = new ArrayList<>();
        for (TradeRecordEntry e : state.tradeHistory()) {
            tradeRecords.add(new TradeHistory.ItemRecord(e.itemId(), e.netProfit(),
                    e.flipsCompleted(), e.qtySold(), e.lastTradedEpochMillis()));
        }
        history.load(tradeRecords);
    }
}
