package com.osrsscripts.core.ge;

import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.MarketStat;
import com.osrsscripts.core.model.PricePoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Selects and ranks flip candidates from live market data. The margin decision uses the trailing
 * hour's <em>averaged</em> prices (insta-sell {@code avgLow} to buy, insta-buy {@code avgHigh} to
 * sell) so a single outlier trade cannot bait a bad item; offers are still <em>placed</em> at the
 * current {@code /latest} price so they fill at market. Liquidity is the lesser of the two
 * directional volumes — a round-trip must fill both a buy and a sell — and candidates are ranked
 * by estimated profit per hour, breaking ties toward the offer that deploys the most capital.
 */
public final class FlipScanner {

    /** A 4-hour buy limit spread over the hours the rate is measured in. */
    private static final double BUY_LIMIT_HOURS = 4.0;

    public List<FlipCandidate> scan(Map<Integer, ItemMeta> mapping,
                                    Map<Integer, PricePoint> latest,
                                    Map<Integer, MarketStat> hourly,
                                    FlipConfig config,
                                    GeTax tax) {
        List<FlipCandidate> candidates = new ArrayList<>();

        for (Map.Entry<Integer, MarketStat> entry : hourly.entrySet()) {
            int itemId = entry.getKey();
            MarketStat stat = entry.getValue();
            long avgBuy = stat.avgLowPrice();
            long avgSell = stat.avgHighPrice();
            if (avgBuy <= 0 || avgSell <= 0) {
                continue; // no trades on a side this hour: can't price it reliably
            }

            long balancedVolume = stat.balancedVolume();
            if (balancedVolume < config.minVolume()) {
                continue; // illiquid on at least one side: a flip would strand here
            }

            long margin = tax.netMarginPerItem(itemId, avgBuy, avgSell);
            if (margin < config.minMarginGp()) {
                continue;
            }
            double roi = (double) margin / avgBuy;
            if (roi < config.minMarginPct()) {
                continue;
            }

            ItemMeta meta = mapping.get(itemId);
            // Unknown membership is treated as members: on F2P a rejected offer would be
            // retried forever, so only items known to be free-to-play pass the filter.
            if (!config.membersItemsAllowed() && (meta == null || meta.members())) {
                continue;
            }

            // Decide on averages, but place at the live price so offers fill at market.
            PricePoint live = latest.get(itemId);
            if (live == null || !live.hasHigh() || !live.hasLow()) {
                continue; // no live price to place against
            }
            int buyLimit = meta != null ? meta.buyLimit() : 0;

            candidates.add(new FlipCandidate(itemId, live.low(), live.high(), margin,
                    balancedVolume, buyLimit, roi));
        }

        long perItemCap = config.perItemCapitalCap();
        candidates.sort(Comparator
                .comparingDouble(FlipScanner::gpPerHour).reversed()
                .thenComparing(Comparator.comparingLong(
                        (FlipCandidate c) -> capitalDeployed(c, perItemCap)).reversed())
                .thenComparingInt(FlipCandidate::itemId));
        return candidates;
    }

    /**
     * Estimated profit per hour: net margin times the units the offer can sustainably round-trip
     * in an hour, throttled by the balanced volume and the per-hour share of the 4h buy limit.
     */
    private static double gpPerHour(FlipCandidate candidate) {
        double unitsPerHour = candidate.buyLimit() > 0
                ? Math.min(candidate.volume(), candidate.buyLimit() / BUY_LIMIT_HOURS)
                : candidate.volume();
        return candidate.netMarginPerItem() * unitsPerHour;
    }

    /**
     * Units one offer could realistically buy in a cycle: throttled by the 4h buy limit, the
     * balanced volume, and how many the per-item capital cap can afford.
     */
    private static long deployableUnits(FlipCandidate candidate, long perItemCap) {
        long throughput = candidate.buyLimit() > 0
                ? Math.min(candidate.buyLimit(), candidate.volume())
                : candidate.volume();
        long byCap = candidate.buyPrice() > 0 ? perItemCap / candidate.buyPrice() : 0;
        return Math.min(throughput, byCap);
    }

    /** Capital one offer deploys — the tiebreak, so equally profitable offers fill big-first. */
    private static long capitalDeployed(FlipCandidate candidate, long perItemCap) {
        return candidate.buyPrice() * deployableUnits(candidate, perItemCap);
    }
}
