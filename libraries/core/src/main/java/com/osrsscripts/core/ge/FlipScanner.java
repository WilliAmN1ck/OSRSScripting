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
 * Selects and ranks flip candidates from live market data. The margin decision uses averaged
 * prices (insta-sell {@code avgLow} to buy, insta-buy {@code avgHigh} to sell) so a single outlier
 * trade cannot bait a bad item — the fresher five-minute averages when an item is actively trading
 * both sides, else the trailing hour's. Items whose five-minute price has dropped sharply below the
 * hour (a falling knife) are skipped for buying. Offers are still <em>placed</em> at the current
 * {@code /latest} price so they fill at market. Liquidity is the lesser of the two directional
 * volumes — a round-trip must fill both a buy and a sell — and candidates are ranked by estimated
 * profit per hour, breaking ties toward the offer that deploys the most capital.
 */
public final class FlipScanner {

    /** A 4-hour buy limit spread over the hours the rate is measured in. */
    private static final double BUY_LIMIT_HOURS = 4.0;

    public List<FlipCandidate> scan(Map<Integer, ItemMeta> mapping,
                                    Map<Integer, PricePoint> latest,
                                    Map<Integer, MarketStat> hourly,
                                    Map<Integer, MarketStat> fiveMinute,
                                    FlipConfig config,
                                    GeTax tax) {
        List<FlipCandidate> candidates = new ArrayList<>();

        for (Map.Entry<Integer, MarketStat> entry : hourly.entrySet()) {
            int itemId = entry.getKey();
            MarketStat stat = entry.getValue();
            MarketStat recent = fiveMinute.get(itemId);

            // Liquidity uses the hour's volume (more data); a flip strands if either side is thin.
            long balancedVolume = stat.balancedVolume();
            if (balancedVolume < config.minVolume()) {
                continue;
            }

            if (MarketTrend.isFallingKnife(recent, stat, MarketTrend.DEFAULT_DROP)) {
                continue; // sell-side price dropping sharply: don't buy into the crash
            }

            // Price the decision off the fresher 5-minute averages when the item is actively
            // trading both sides there, otherwise the steadier hour averages.
            MarketStat priceStat = hasBothSides(recent) ? recent : stat;
            long avgBuy = priceStat.avgLowPrice();
            long avgSell = priceStat.avgHighPrice();
            if (avgBuy <= 0 || avgSell <= 0) {
                continue; // no trades on a side: can't price it reliably
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

    /** Whether a stat priced both sides in its window, so its averages are usable. */
    private static boolean hasBothSides(MarketStat stat) {
        return stat != null && stat.avgHighPrice() > 0 && stat.avgLowPrice() > 0;
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
