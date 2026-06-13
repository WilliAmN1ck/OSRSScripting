package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.MarketStat;
import com.osrsscripts.core.model.PricePoint;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlipScannerTest {

    private final GeTax tax = new GeTax(new GeTaxRules(0.02, 5_000_000L, 100L,
            java.util.Collections.emptySet()));
    private final FlipScanner scanner = new FlipScanner();
    private final Instant t = Instant.parse("2026-06-11T12:00:00Z");

    /** No five-minute data: the scanner uses the hour averages and applies no downtrend guard. */
    private static final Map<Integer, MarketStat> NO_5M = Map.of();

    private Map<Integer, ItemMeta> mapping() {
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(100, new ItemMeta(100, "A", false, 1000));
        m.put(200, new ItemMeta(200, "B", true, 100)); // members item
        m.put(300, new ItemMeta(300, "C", false, 5000));
        return m;
    }

    /** Live prices mirror the hourly averages unless a test overrides one to probe placement. */
    private Map<Integer, PricePoint> latest() {
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(100, new PricePoint(1100, t, 1000, t));
        p.put(200, new PricePoint(2100, t, 2000, t));
        p.put(300, new PricePoint(505, t, 500, t));
        return p;
    }

    private Map<Integer, MarketStat> hourly() {
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(100, new MarketStat(1100, 1000, 600, 600)); // margin 78, roi .078, balanced 600
        h.put(200, new MarketStat(2100, 2000, 50, 50));   // margin 58, roi .029, balanced 50
        h.put(300, new MarketStat(505, 500, 9999, 9999)); // margin -5 -> excluded
        return h;
    }

    @Test
    void rejectsNonPositiveMarginAndMissingData() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();
        List<FlipCandidate> result = scanner.scan(mapping(), latest(), hourly(), NO_5M, config, tax);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(c -> c.itemId() == 300));
    }

    @Test
    void liquidityUsesTheLesserDirectionalVolumeNotTheSum() {
        // 4000 + 10 = 4010 total clears a 1000 floor, but only 10 sell-side fills an hour means a
        // flip would strand on the sell. The balanced (lesser) volume governs, so it is rejected.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "lopsided", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1100, t, 1000, t));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1100, 1000, 4000, 10));
        FlipConfig config = FlipConfig.builder().minMarginGp(1).minVolume(1000).build();

        assertTrue(scanner.scan(m, p, h, NO_5M, config, tax).isEmpty());
    }

    @Test
    void marginIsJudgedOnHourlyAveragesNotALatestOutlier() {
        // A single outlier /latest trade makes the spread look fat (buy 1000, sell 1300), but the
        // hour's averages show a thin, losing spread. The averaged decision rejects it.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "outlier", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1300, t, 1000, t)); // tempting latest spread
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1010, 1000, 5000, 5000)); // real hour: margin < 0 after tax
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        assertTrue(scanner.scan(m, p, h, NO_5M, config, tax).isEmpty());
    }

    @Test
    void placesAtTheLivePriceWhileDecidingOnAverages() {
        // Averages clear the filters; the offer prices come from /latest, not the averages.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "x", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1205, t, 995, t)); // live placement prices
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1200, 1000, 5000, 5000)); // averaged decision prices
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        FlipCandidate c = scanner.scan(m, p, h, NO_5M, config, tax).get(0);
        // Buy is the live low (995, not avg 1000) plus the bid-up: gross margin 176, 5% = 9.
        assertEquals(1004, c.buyPrice());
        assertEquals(1205, c.sellPrice()); // live high, not avg 1200
    }

    @Test
    void bidsAboveTheLiveLowByAFractionOfTheMargin() {
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "x", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1100, t, 1000, t));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1100, 1000, 5000, 5000));
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        // Gross margin = 1100 - 1000 - tax(22) = 78; bid-up = round(78 x 5%) = 4.
        FlipCandidate c = scanner.scan(m, p, h, NO_5M, config, tax).get(0);
        assertEquals(1004, c.buyPrice(), "live low 1000 + 5% of margin");
        assertEquals(74L, c.netMarginPerItem(), "margin is reported net of the bid-up");
    }

    @Test
    void skipsItemsWithNoLivePriceToPlaceAgainst() {
        // Has hourly stats but no /latest entry: nothing to place an offer at, so it is skipped.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "stale", false, 1000));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1200, 1000, 5000, 5000));
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        assertTrue(scanner.scan(m, new HashMap<>(), h, NO_5M, config, tax).isEmpty());
    }

    @Test
    void skipsAFallingKnifeWhoseFiveMinutePriceDroppedSharply() {
        // The hour looks fine, but the 5-minute sell-side price is down ~6% (940 < 1000 x 0.95):
        // a crash in progress, so the buy is skipped.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "crashing", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1100, t, 1000, t));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1100, 1000, 5000, 5000));
        Map<Integer, MarketStat> five = new HashMap<>();
        five.put(1, new MarketStat(1040, 940, 200, 200));
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        assertTrue(scanner.scan(m, p, h, five, config, tax).isEmpty());
    }

    @Test
    void aMildFiveMinuteDipDoesNotTripTheGuard() {
        // Only ~4% down (960 > 1000 x 0.95): normal fluctuation, still a candidate.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "wobbling", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1100, t, 1000, t));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1100, 1000, 5000, 5000));
        Map<Integer, MarketStat> five = new HashMap<>();
        five.put(1, new MarketStat(1100, 960, 200, 200));
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        List<FlipCandidate> result = scanner.scan(m, p, h, five, config, tax);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).itemId());
    }

    @Test
    void usesTheFresherFiveMinuteAveragesForTheMarginWhenBothSidesTraded() {
        // On the hour averages the spread is a post-tax loss (margin -10) and would be rejected; the
        // fresher 5-minute averages show a healthy spread, so it qualifies.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "recovering", false, 1000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1100, t, 1000, t));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1010, 1000, 5000, 5000)); // hour: margin -10 -> would reject
        Map<Integer, MarketStat> five = new HashMap<>();
        five.put(1, new MarketStat(1100, 1000, 300, 300)); // 5m: margin 78 -> qualifies
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();

        List<FlipCandidate> result = scanner.scan(m, p, h, five, config, tax);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).itemId());
    }

    @Test
    void ranksByEstimatedProfitPerHour() {
        // Cheap item: margin ~194, sustainable 1000/hr (buy-limit bound) -> ~194k/hr.
        // Pricey item: margin ~1940, buy limit 30 -> 7.5/hr -> ~14.5k/hr. The faster item wins.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "fast", false, 4000));   // buy limit 4000 -> 1000/hr
        m.put(2, new ItemMeta(2, "slow", false, 30));     // buy limit 30 -> 7.5/hr
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(300, t, 100, t));
        p.put(2, new PricePoint(53_000, t, 50_000, t));
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(300, 100, 5000, 5000));
        h.put(2, new MarketStat(53_000, 50_000, 5000, 5000));
        FlipConfig config = FlipConfig.builder()
                .minMarginGp(1).perItemCapitalCap(Long.MAX_VALUE).build();

        List<FlipCandidate> result = scanner.scan(m, p, h, NO_5M, config, tax);

        assertEquals(1, result.get(0).itemId(), "the faster, higher gp/hr item ranks first");
        assertEquals(2, result.get(1).itemId());
    }

    @Test
    void breaksGpPerHourTiesTowardMoreCapitalDeployed() {
        // Generous buy limits, so the balanced volume sets the rate. Post bid-up the net margins are
        // 76 and 228, and gp/hr ties at 22,800 (76 x 300 == 228 x 100). The pricier offer deploys
        // far more capital (~1.0M vs ~0.3M), so it wins the tiebreak.
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(1, new ItemMeta(1, "cheap", false, 4000));
        m.put(2, new ItemMeta(2, "pricey", false, 4000));
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(1, new PricePoint(1_102, t, 1_000, t));     // gross margin 80
        p.put(2, new PricePoint(10_448, t, 10_000, t));   // gross margin 240
        Map<Integer, MarketStat> h = new HashMap<>();
        h.put(1, new MarketStat(1_102, 1_000, 300, 300));
        h.put(2, new MarketStat(10_448, 10_000, 100, 100));
        FlipConfig config = FlipConfig.builder()
                .minMarginGp(1).perItemCapitalCap(Long.MAX_VALUE).build();

        List<FlipCandidate> result = scanner.scan(m, p, h, NO_5M, config, tax);

        assertEquals(2, result.get(0).itemId(), "equal gp/hr -> the capital-heavier offer first");
        assertEquals(1, result.get(1).itemId());
    }

    @Test
    void appliesVolumeFloor() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).minVolume(200).build();
        List<FlipCandidate> result = scanner.scan(mapping(), latest(), hourly(), NO_5M, config, tax);
        assertEquals(1, result.size());
        assertEquals(100, result.get(0).itemId()); // 200 has balanced 50 < 200
    }

    @Test
    void excludesMembersItemsWhenDisallowed() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).membersItemsAllowed(false).build();
        List<FlipCandidate> result = scanner.scan(mapping(), latest(), hourly(), NO_5M, config, tax);
        assertEquals(1, result.size());
        assertEquals(100, result.get(0).itemId());
    }

    @Test
    void includesMembersItemsByDefault() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();
        List<FlipCandidate> result = scanner.scan(mapping(), latest(), hourly(), NO_5M, config, tax);
        assertTrue(result.stream().anyMatch(c -> c.itemId() == 200));
    }

    @Test
    void appliesRoiFloor() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).minMarginPct(0.05).build();
        List<FlipCandidate> result = scanner.scan(mapping(), latest(), hourly(), NO_5M, config, tax);
        assertEquals(1, result.size());
        assertEquals(100, result.get(0).itemId()); // roi .078 passes; 200 roi .029 fails
    }
}
