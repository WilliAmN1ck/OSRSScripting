package com.osrsscripts.core.ge;

import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.VolumePoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Selects and ranks flip candidates from live market data. An item buys at its instant-sell price
 * ({@code low}) and sells at its instant-buy price ({@code high}); candidates failing the config's
 * margin, ROI, or volume floors are discarded. Ranking favours the capital each offer would
 * deploy — so a large bankroll is put to work rather than parked behind small, high-volume flips —
 * breaking ties by the profit that capital earns per cycle.
 */
public final class FlipScanner {

    public List<FlipCandidate> scan(Map<Integer, ItemMeta> mapping,
                                    Map<Integer, PricePoint> prices,
                                    Map<Integer, VolumePoint> volumes,
                                    FlipConfig config,
                                    GeTax tax) {
        List<FlipCandidate> candidates = new ArrayList<>();

        for (Map.Entry<Integer, PricePoint> entry : prices.entrySet()) {
            int itemId = entry.getKey();
            PricePoint price = entry.getValue();
            if (!price.hasHigh() || !price.hasLow()) {
                continue;
            }
            long buyPrice = price.low();
            long sellPrice = price.high();

            long margin = tax.netMarginPerItem(itemId, buyPrice, sellPrice);
            if (margin < config.minMarginGp()) {
                continue;
            }
            double roi = (double) margin / buyPrice;
            if (roi < config.minMarginPct()) {
                continue;
            }
            VolumePoint volumePoint = volumes.get(itemId);
            long volume = volumePoint != null ? volumePoint.total() : 0L;
            if (volume < config.minVolume()) {
                continue;
            }
            ItemMeta meta = mapping.get(itemId);
            // Unknown membership is treated as members: on F2P a rejected offer would be
            // retried forever, so only items known to be free-to-play pass the filter.
            if (!config.membersItemsAllowed() && (meta == null || meta.members())) {
                continue;
            }
            int buyLimit = meta != null ? meta.buyLimit() : 0;

            candidates.add(new FlipCandidate(itemId, buyPrice, sellPrice, margin, volume, buyLimit,
                    roi));
        }

        long perItemCap = config.perItemCapitalCap();
        candidates.sort(Comparator
                .comparingLong((FlipCandidate c) -> capitalDeployed(c, perItemCap)).reversed()
                .thenComparing(Comparator.comparingLong(
                        (FlipCandidate c) -> profitPerCycle(c, perItemCap)).reversed())
                .thenComparingInt(FlipCandidate::itemId));
        return candidates;
    }

    /**
     * Units one offer could realistically buy in a cycle: throttled by the 4h buy limit, the
     * traded volume, and how many the per-item capital cap can afford.
     */
    private static long deployableUnits(FlipCandidate candidate, long perItemCap) {
        long throughput = candidate.buyLimit() > 0
                ? Math.min(candidate.buyLimit(), candidate.volume())
                : candidate.volume();
        long byCap = candidate.buyPrice() > 0 ? perItemCap / candidate.buyPrice() : 0;
        return Math.min(throughput, byCap);
    }

    /** Capital one offer would deploy — the primary ranking key, so big offers fill slots first. */
    private static long capitalDeployed(FlipCandidate candidate, long perItemCap) {
        return candidate.buyPrice() * deployableUnits(candidate, perItemCap);
    }

    /** Profit that capital earns per cycle — the tiebreak among offers of equal size. */
    private static long profitPerCycle(FlipCandidate candidate, long perItemCap) {
        return candidate.netMarginPerItem() * deployableUnits(candidate, perItemCap);
    }
}
