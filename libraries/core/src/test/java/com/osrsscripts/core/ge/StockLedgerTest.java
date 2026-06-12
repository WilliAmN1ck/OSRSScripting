package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockLedgerTest {

    private static final int ITEM = 4151;

    @Test
    void buyAddsOwnedQuantity() {
        StockLedger ledger = new StockLedger();

        ledger.recordBuy(ITEM, 10, 100_000L);
        ledger.recordBuy(ITEM, 5, 110_000L);

        assertEquals(15, ledger.ownedQty(ITEM));
        assertEquals(Map.of(ITEM, 15), ledger.ownedQuantities());
    }

    @Test
    void unknownItemOwnsZero() {
        StockLedger ledger = new StockLedger();

        assertEquals(0, ledger.ownedQty(999));
        assertTrue(ledger.ownedQuantities().isEmpty());
    }

    @Test
    void sellConsumesOldestLotsFirstAndReturnsCostBasis() {
        StockLedger ledger = new StockLedger();
        ledger.recordBuy(ITEM, 10, 100L);
        ledger.recordBuy(ITEM, 10, 200L);

        // 10 @ 100 + 5 @ 200 = 2000
        assertEquals(2_000L, ledger.recordSell(ITEM, 15));
        assertEquals(5, ledger.ownedQty(ITEM));
        // remaining 5 @ 200
        assertEquals(1_000L, ledger.recordSell(ITEM, 5));
        assertEquals(0, ledger.ownedQty(ITEM));
    }

    @Test
    void sellClampsToOwnedQuantity() {
        StockLedger ledger = new StockLedger();
        ledger.recordBuy(ITEM, 3, 100L);

        // Only 3 owned: basis covers what existed.
        assertEquals(300L, ledger.recordSell(ITEM, 10));
        assertEquals(0, ledger.ownedQty(ITEM));
        // Selling an item never bought consumes nothing.
        assertEquals(0L, ledger.recordSell(999, 5));
    }

    @Test
    void lotsRoundTripThroughLoad() {
        StockLedger ledger = new StockLedger();
        ledger.recordBuy(ITEM, 10, 100L);
        ledger.recordBuy(561, 50, 200L);

        List<StockLedger.Lot> snapshot = ledger.lots();

        StockLedger restored = new StockLedger();
        restored.load(snapshot);

        assertEquals(10, restored.ownedQty(ITEM));
        assertEquals(50, restored.ownedQty(561));
        // FIFO order preserved across the round trip.
        assertEquals(1_000L, restored.recordSell(ITEM, 10));
    }
}
