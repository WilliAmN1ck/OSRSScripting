package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BuyLimitLedgerTest {

    private static final int ITEM = 561; // nature rune
    private final Instant now = Instant.parse("2026-06-11T12:00:00Z");

    @Test
    void countsOnlyPurchasesWithinTheWindow() {
        BuyLimitLedger ledger = new BuyLimitLedger(Duration.ofHours(4));
        ledger.recordPurchase(ITEM, 100, now.minus(Duration.ofHours(1)));   // in
        ledger.recordPurchase(ITEM, 50, now.minus(Duration.ofHours(3)));    // in
        ledger.recordPurchase(ITEM, 999, now.minus(Duration.ofHours(5)));   // expired
        assertEquals(150, ledger.purchasedWithin(ITEM, now));
    }

    @Test
    void doesNotCountOtherItems() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.recordPurchase(ITEM, 100, now);
        ledger.recordPurchase(4151, 200, now);
        assertEquals(100, ledger.purchasedWithin(ITEM, now));
    }

    @Test
    void remainingIsLimitMinusPurchasedFlooredAtZero() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.recordPurchase(ITEM, 100, now);
        assertEquals(28000, ledger.remaining(ITEM, 28100, now));
        ledger.recordPurchase(ITEM, 28100, now);
        assertEquals(0, ledger.remaining(ITEM, 28100, now));
    }

    @Test
    void unknownLimitIsUnconstrained() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        assertEquals(Integer.MAX_VALUE, ledger.remaining(ITEM, 0, now));
    }

    @Test
    void pruneRemovesExpiredEntries() {
        BuyLimitLedger ledger = new BuyLimitLedger(Duration.ofHours(4));
        ledger.recordPurchase(ITEM, 100, now.minus(Duration.ofHours(1)));
        ledger.recordPurchase(ITEM, 50, now.minus(Duration.ofHours(5)));
        ledger.prune(now);
        assertEquals(1, ledger.purchases().size());
        assertEquals(100, ledger.purchasedWithin(ITEM, now));
    }
}
