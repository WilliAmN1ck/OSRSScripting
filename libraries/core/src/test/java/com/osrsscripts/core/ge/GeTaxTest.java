package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeTaxTest {

    private static final int EXEMPT_ITEM = 13190; // e.g. an old-school bond
    private static final int NORMAL_ITEM = 4151;  // e.g. an abyssal whip

    private GeTax tax(Set<Integer> exemptItems) {
        return new GeTax(new GeTaxRules(0.02, 5_000_000L, 100L, exemptItems));
    }

    @Test
    void chargesTwoPercentFlooredForANormalItem() {
        GeTax tax = tax(Collections.emptySet());
        assertEquals(20L, tax.taxPerItem(NORMAL_ITEM, 1_000L));
        // 0.02 * 1100 = 22.0 -> 22
        assertEquals(22L, tax.taxPerItem(NORMAL_ITEM, 1_100L));
        // 0.02 * 1051 = 21.02 -> floored to 21
        assertEquals(21L, tax.taxPerItem(NORMAL_ITEM, 1_051L));
    }

    @Test
    void exemptsItemsAtOrBelowThreshold() {
        GeTax tax = tax(Collections.emptySet());
        assertEquals(0L, tax.taxPerItem(NORMAL_ITEM, 100L));
        assertEquals(0L, tax.taxPerItem(NORMAL_ITEM, 50L));
    }

    @Test
    void exemptsListedItemsRegardlessOfPrice() {
        GeTax tax = tax(new HashSet<>(Collections.singletonList(EXEMPT_ITEM)));
        assertEquals(0L, tax.taxPerItem(EXEMPT_ITEM, 5_000_000L));
    }

    @Test
    void capsTaxPerItem() {
        GeTax tax = tax(Collections.emptySet());
        // 0.02 * 1,000,000,000 = 20,000,000 -> capped at 5,000,000
        assertEquals(5_000_000L, tax.taxPerItem(NORMAL_ITEM, 1_000_000_000L));
    }

    @Test
    void taxOnSaleMultipliesByQuantity() {
        GeTax tax = tax(Collections.emptySet());
        assertEquals(200L, tax.taxOnSale(NORMAL_ITEM, 1_000L, 10));
    }

    @Test
    void netMarginSubtractsBuyPriceAndSellTax() {
        GeTax tax = tax(Collections.emptySet());
        // sell 1100, buy 1000, tax on sell = 22 -> 78
        assertEquals(78L, tax.netMarginPerItem(NORMAL_ITEM, 1_000L, 1_100L));
    }
}
