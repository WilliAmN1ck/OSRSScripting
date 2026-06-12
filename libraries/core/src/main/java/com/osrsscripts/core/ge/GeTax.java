package com.osrsscripts.core.ge;

/**
 * Computes Grand Exchange sale tax and tax-adjusted margins from a set of {@link GeTaxRules}.
 *
 * <p>The rate is applied to basis-point precision to avoid floating-point drift, then floored
 * (matching the game's behaviour) and capped per item.
 */
public final class GeTax {

    private final GeTaxRules rules;
    private final long basisPoints;

    public GeTax(GeTaxRules rules) {
        this.rules = rules;
        this.basisPoints = Math.round(rules.rate() * 10_000);
    }

    /** Tax charged on selling a single unit of {@code itemId} at {@code unitPrice}. */
    public long taxPerItem(int itemId, long unitPrice) {
        if (rules.exemptItems().contains(itemId)) {
            return 0L;
        }
        if (unitPrice <= rules.exemptBelow()) {
            return 0L;
        }
        long tax = (unitPrice * basisPoints) / 10_000;
        return Math.min(tax, rules.perItemCap());
    }

    /** Total tax on selling {@code qty} units of {@code itemId} at {@code unitPrice} each. */
    public long taxOnSale(int itemId, long unitPrice, int qty) {
        return taxPerItem(itemId, unitPrice) * qty;
    }

    /** Net margin per item after tax: {@code sellPrice - buyPrice - taxPerItem(sellPrice)}. */
    public long netMarginPerItem(int itemId, long buyPrice, long sellPrice) {
        return sellPrice - buyPrice - taxPerItem(itemId, sellPrice);
    }
}
