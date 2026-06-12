package com.osrsscripts.core.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.model.FlipCandidate;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.VolumePoint;
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

    private Map<Integer, ItemMeta> mapping() {
        Map<Integer, ItemMeta> m = new HashMap<>();
        m.put(100, new ItemMeta(100, "A", false, 1000));
        m.put(200, new ItemMeta(200, "B", true, 100)); // members item
        m.put(300, new ItemMeta(300, "C", false, 5000));
        return m;
    }

    private Map<Integer, PricePoint> prices() {
        Map<Integer, PricePoint> p = new HashMap<>();
        p.put(100, new PricePoint(1100, t, 1000, t)); // margin 78, roi .078
        p.put(200, new PricePoint(2100, t, 2000, t)); // margin 58, roi .029
        p.put(300, new PricePoint(505, t, 500, t));   // margin -5 -> excluded
        p.put(400, new PricePoint(0, t, 0, t));       // no data -> excluded
        return p;
    }

    private Map<Integer, VolumePoint> volumes() {
        Map<Integer, VolumePoint> v = new HashMap<>();
        v.put(100, new VolumePoint(600, 600));  // 1200
        v.put(200, new VolumePoint(50, 50));    // 100
        v.put(300, new VolumePoint(9999, 9999));
        return v;
    }

    @Test
    void rejectsNonPositiveMarginAndMissingData() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();
        List<FlipCandidate> result = scanner.scan(mapping(), prices(), volumes(), config, tax);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(c -> c.itemId() == 300 || c.itemId() == 400));
    }

    @Test
    void ranksByMarginScaledByThroughput() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();
        List<FlipCandidate> result = scanner.scan(mapping(), prices(), volumes(), config, tax);
        // 100: 78 * min(1000,1200)=78000 ; 200: 58 * min(100,100)=5800
        assertEquals(100, result.get(0).itemId());
        assertEquals(200, result.get(1).itemId());
    }

    @Test
    void appliesVolumeFloor() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).minVolume(200).build();
        List<FlipCandidate> result = scanner.scan(mapping(), prices(), volumes(), config, tax);
        assertEquals(1, result.size());
        assertEquals(100, result.get(0).itemId());
    }

    @Test
    void excludesMembersItemsWhenDisallowed() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).membersItemsAllowed(false).build();
        List<FlipCandidate> result = scanner.scan(mapping(), prices(), volumes(), config, tax);
        // 200 is members; 400 has no mapping entry, so membership is unknown -> excluded too.
        assertEquals(1, result.size());
        assertEquals(100, result.get(0).itemId());
    }

    @Test
    void includesMembersItemsByDefault() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).build();
        List<FlipCandidate> result = scanner.scan(mapping(), prices(), volumes(), config, tax);
        assertTrue(result.stream().anyMatch(c -> c.itemId() == 200));
    }

    @Test
    void appliesRoiFloor() {
        FlipConfig config = FlipConfig.builder().minMarginGp(1).minMarginPct(0.05).build();
        List<FlipCandidate> result = scanner.scan(mapping(), prices(), volumes(), config, tax);
        assertEquals(1, result.size());
        assertEquals(100, result.get(0).itemId());
    }
}
