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
 * margin, ROI, or volume floors are discarded. Ranking favours net margin scaled by realistic
 * throughput (the smaller of buy limit and traded volume).
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
            int buyLimit = meta != null ? meta.buyLimit() : 0;

            candidates.add(new FlipCandidate(itemId, buyPrice, sellPrice, margin, volume, buyLimit,
                    roi));
        }

        candidates.sort(Comparator.comparingLong(this::score).reversed()
                .thenComparingInt(FlipCandidate::itemId));
        return candidates;
    }

    private long score(FlipCandidate candidate) {
        long throughput = candidate.buyLimit() > 0
                ? Math.min(candidate.buyLimit(), candidate.volume())
                : candidate.volume();
        return candidate.netMarginPerItem() * throughput;
    }
}
