package com.osrsscripts.core.model;

import java.util.Objects;

/**
 * A flip opportunity produced by the scanner: an item with its target buy/sell prices and the
 * net (post-tax) margin, volume and buy limit used to rank it.
 */
public final class FlipCandidate {

    private final int itemId;
    private final long buyPrice;
    private final long sellPrice;
    private final long netMarginPerItem;
    private final long volume;
    private final int buyLimit;
    private final double roi;

    public FlipCandidate(int itemId, long buyPrice, long sellPrice, long netMarginPerItem,
                         long volume, int buyLimit, double roi) {
        this.itemId = itemId;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.netMarginPerItem = netMarginPerItem;
        this.volume = volume;
        this.buyLimit = buyLimit;
        this.roi = roi;
    }

    public int itemId() {
        return itemId;
    }

    public long buyPrice() {
        return buyPrice;
    }

    public long sellPrice() {
        return sellPrice;
    }

    public long netMarginPerItem() {
        return netMarginPerItem;
    }

    public long volume() {
        return volume;
    }

    public int buyLimit() {
        return buyLimit;
    }

    public double roi() {
        return roi;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlipCandidate)) {
            return false;
        }
        FlipCandidate other = (FlipCandidate) o;
        return itemId == other.itemId
                && buyPrice == other.buyPrice
                && sellPrice == other.sellPrice
                && netMarginPerItem == other.netMarginPerItem
                && volume == other.volume
                && buyLimit == other.buyLimit
                && Double.compare(roi, other.roi) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, buyPrice, sellPrice, netMarginPerItem, volume, buyLimit, roi);
    }

    @Override
    public String toString() {
        return "FlipCandidate{itemId=" + itemId + ", buy=" + buyPrice + ", sell=" + sellPrice
                + ", margin=" + netMarginPerItem + ", volume=" + volume + ", buyLimit=" + buyLimit
                + '}';
    }
}
