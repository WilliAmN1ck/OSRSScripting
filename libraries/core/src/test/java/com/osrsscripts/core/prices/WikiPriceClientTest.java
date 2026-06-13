package com.osrsscripts.core.prices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.MarketStat;
import com.osrsscripts.core.testutil.AdjustableClock;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WikiPriceClientTest {

    /** Returns canned bodies per URL and counts how many times each URL was fetched. */
    private static final class CountingFetcher implements HttpFetcher {
        final Map<String, String> bodies = new HashMap<>();
        final Map<String, Integer> calls = new HashMap<>();

        @Override
        public String get(String url) throws IOException {
            calls.merge(url, 1, Integer::sum);
            String body = bodies.get(url);
            if (body == null) {
                throw new IOException("no canned body for " + url);
            }
            return body;
        }
    }

    private WikiPriceClient client(CountingFetcher fetcher, AdjustableClock clock) {
        return new WikiPriceClient(fetcher, clock, "base",
                Duration.ofSeconds(30), Duration.ofHours(6));
    }

    @Test
    void parsesMapping() throws IOException {
        CountingFetcher fetcher = new CountingFetcher();
        fetcher.bodies.put("base/mapping",
                "[{\"id\":4151,\"name\":\"Abyssal whip\",\"members\":true,\"limit\":70},"
                        + "{\"id\":2,\"name\":\"Cannonball\",\"members\":true}]");
        Map<Integer, ItemMeta> mapping =
                client(fetcher, new AdjustableClock(Instant.EPOCH)).mapping();

        assertEquals(new ItemMeta(4151, "Abyssal whip", true, 70), mapping.get(4151));
        // missing "limit" defaults to 0
        assertEquals(new ItemMeta(2, "Cannonball", true, 0), mapping.get(2));
    }

    @Test
    void parsesLatestTreatingNullPricesAsNoData() throws IOException {
        CountingFetcher fetcher = new CountingFetcher();
        fetcher.bodies.put("base/latest",
                "{\"data\":{\"4151\":{\"high\":2000000,\"highTime\":1700000000,"
                        + "\"low\":1950000,\"lowTime\":1700000100},"
                        + "\"2\":{\"high\":null,\"highTime\":null,\"low\":180,\"lowTime\":1700000200}}}");
        Map<Integer, PricePoint> latest =
                client(fetcher, new AdjustableClock(Instant.EPOCH)).latest();

        PricePoint whip = latest.get(4151);
        assertEquals(2000000, whip.high());
        assertEquals(1950000, whip.low());
        assertTrue(whip.hasHigh());

        PricePoint cannonball = latest.get(2);
        assertFalse(cannonball.hasHigh());
        assertTrue(cannonball.hasLow());
    }

    @Test
    void parsesOneHourStats() throws IOException {
        CountingFetcher fetcher = new CountingFetcher();
        fetcher.bodies.put("base/1h",
                "{\"data\":{\"4151\":{\"avgHighPrice\":2600,\"avgLowPrice\":2500,"
                        + "\"highPriceVolume\":120,\"lowPriceVolume\":80}}}");
        Map<Integer, MarketStat> hourly =
                client(fetcher, new AdjustableClock(Instant.EPOCH)).hourlyStats();

        assertEquals(new MarketStat(2600, 2500, 120, 80), hourly.get(4151));
        assertEquals(80, hourly.get(4151).balancedVolume());
    }

    @Test
    void cachesWithinTtlAndRefetchesAfter() throws IOException {
        CountingFetcher fetcher = new CountingFetcher();
        fetcher.bodies.put("base/latest", "{\"data\":{\"2\":{\"high\":10,\"low\":9}}}");
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-11T12:00:00Z"));
        WikiPriceClient client = client(fetcher, clock);

        client.latest();
        client.latest(); // within 30s TTL -> served from cache
        assertEquals(1, fetcher.calls.get("base/latest"));

        clock.advance(Duration.ofSeconds(31));
        client.latest(); // TTL expired -> refetch
        assertEquals(2, fetcher.calls.get("base/latest"));
    }
}
