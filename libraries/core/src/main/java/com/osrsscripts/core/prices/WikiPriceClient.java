package com.osrsscripts.core.prices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osrsscripts.core.model.ItemMeta;
import com.osrsscripts.core.model.PricePoint;
import com.osrsscripts.core.model.VolumePoint;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Reads market data from the OSRS Wiki real-time prices API, caching each endpoint for a TTL so a
 * tight scanning loop does not hammer the service. All parsing tolerates missing/null fields.
 */
public final class WikiPriceClient {

    public static final String DEFAULT_BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";

    private final HttpFetcher fetcher;
    private final Clock clock;
    private final String baseUrl;
    private final Duration liveTtl;
    private final Duration mappingTtl;
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<Integer, ItemMeta> cachedMapping;
    private Instant mappingFetchedAt;
    private Map<Integer, PricePoint> cachedLatest;
    private Instant latestFetchedAt;
    private Map<Integer, VolumePoint> cachedVolumes;
    private Instant volumesFetchedAt;

    public WikiPriceClient(HttpFetcher fetcher, Clock clock, String baseUrl,
                           Duration liveTtl, Duration mappingTtl) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.liveTtl = liveTtl;
        this.mappingTtl = mappingTtl;
    }

    /** Convenience client against the live API. */
    public static WikiPriceClient live(String userAgent) {
        return new WikiPriceClient(new WikiHttpFetcher(userAgent), Clock.systemUTC(),
                DEFAULT_BASE_URL, Duration.ofSeconds(30), Duration.ofHours(6));
    }

    /** Static item metadata (name, members, buy limit), keyed by item id. */
    public Map<Integer, ItemMeta> mapping() throws IOException {
        if (fresh(mappingFetchedAt, mappingTtl)) {
            return cachedMapping;
        }
        JsonNode root = mapper.readTree(fetcher.get(baseUrl + "/mapping"));
        Map<Integer, ItemMeta> result = new HashMap<>();
        for (JsonNode node : root) {
            int id = node.path("id").asInt();
            result.put(id, new ItemMeta(id, node.path("name").asText(""),
                    node.path("members").asBoolean(false), node.path("limit").asInt(0)));
        }
        cachedMapping = result;
        mappingFetchedAt = clock.instant();
        return result;
    }

    /** Latest instant-buy/instant-sell prices, keyed by item id. */
    public Map<Integer, PricePoint> latest() throws IOException {
        if (fresh(latestFetchedAt, liveTtl)) {
            return cachedLatest;
        }
        JsonNode data = mapper.readTree(fetcher.get(baseUrl + "/latest")).path("data");
        Map<Integer, PricePoint> result = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            int id = Integer.parseInt(entry.getKey());
            JsonNode n = entry.getValue();
            long high = n.path("high").asLong(0);
            long low = n.path("low").asLong(0);
            result.put(id, new PricePoint(high, epochSeconds(n, "highTime"),
                    low, epochSeconds(n, "lowTime")));
        }
        cachedLatest = result;
        latestFetchedAt = clock.instant();
        return result;
    }

    /** Trade volumes over the trailing hour, keyed by item id. */
    public Map<Integer, VolumePoint> volumesOneHour() throws IOException {
        if (fresh(volumesFetchedAt, liveTtl)) {
            return cachedVolumes;
        }
        JsonNode data = mapper.readTree(fetcher.get(baseUrl + "/1h")).path("data");
        Map<Integer, VolumePoint> result = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            int id = Integer.parseInt(entry.getKey());
            JsonNode n = entry.getValue();
            result.put(id, new VolumePoint(n.path("highPriceVolume").asLong(0),
                    n.path("lowPriceVolume").asLong(0)));
        }
        cachedVolumes = result;
        volumesFetchedAt = clock.instant();
        return result;
    }

    private boolean fresh(Instant fetchedAt, Duration ttl) {
        return fetchedAt != null && Duration.between(fetchedAt, clock.instant()).compareTo(ttl) < 0;
    }

    private static Instant epochSeconds(JsonNode node, String field) {
        long seconds = node.path(field).asLong(0);
        return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
    }
}
