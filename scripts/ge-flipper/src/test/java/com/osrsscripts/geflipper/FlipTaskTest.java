package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.ge.BuyLimitLedger;
import com.osrsscripts.core.ge.FlipEngine;
import com.osrsscripts.core.ge.FlipScanner;
import com.osrsscripts.core.ge.GeTax;
import com.osrsscripts.core.ge.GeTaxRules;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.prices.HttpFetcher;
import com.osrsscripts.core.prices.WikiPriceClient;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlipTaskTest {

    private static final String BASE_URL = "http://test/api";

    private final FakeGeClient client = new FakeGeClient();
    private final FlipScanner scanner = new FlipScanner();
    private final FlipEngine engine = new FlipEngine();
    private final GeTax tax = new GeTax(GeTaxRules.defaults());
    private final BuyLimitLedger ledger = new BuyLimitLedger();

    private FlipTask task(HttpFetcher fetcher) {
        WikiPriceClient prices = new WikiPriceClient(
                fetcher, Clock.systemUTC(), BASE_URL, Duration.ofMinutes(5), Duration.ofHours(6));
        return new FlipTask(client, prices, scanner, engine, tax, ledger, buyingConfig(),
                new FlipActionExecutor(client));
    }

    private static FlipConfig buyingConfig() {
        return FlipConfig.builder()
                .capitalCap(1_000_000L)
                .perItemCapitalCap(1_000_000L)
                .minMarginGp(1L)
                .minMarginPct(0.0)
                .minVolume(0L)
                .maxSlots(8)
                .maxOfferAge(Duration.ofMinutes(30))
                .build();
    }

    @Test
    void shouldRunFollowsClientOpenState() {
        FlipTask task = task(new CannedFetcher());

        client.open = true;
        assertTrue(task.shouldRun());
        client.open = false;
        assertFalse(task.shouldRun());
    }

    @Test
    void executeBuysTheRankedCandidate() {
        client.open = true;
        client.coins = 1_000L;                       // affords 10 @ 100 gp
        client.offers = OfferMapper.fillEightSlots(List.of()); // eight free slots

        task(new CannedFetcher()).execute();

        assertEquals(1, client.buys.size(), "one buy offer placed");
        assertArrayEquals(new int[] {100, 100, 10}, client.buys.get(0));
        assertTrue(client.sells.isEmpty());
    }

    @Test
    void executeSwallowsMarketFetchFailure() {
        client.open = true;
        client.coins = 1_000L;
        client.offers = OfferMapper.fillEightSlots(List.of());

        task(url -> {
            throw new IOException("network down");
        }).execute();

        assertTrue(client.buys.isEmpty(), "no actions on a failed market fetch");
        assertEquals(0, client.collectCalls);
    }

    /** Serves the three wiki endpoints for a single profitable item (id 100, buy 100 / sell 200). */
    private static final class CannedFetcher implements HttpFetcher {
        @Override
        public String get(String url) {
            if (url.endsWith("/mapping")) {
                return "[{\"id\":100,\"name\":\"Test item\",\"members\":false,\"limit\":1000}]";
            }
            if (url.endsWith("/latest")) {
                return "{\"data\":{\"100\":{\"high\":200,\"highTime\":1,\"low\":100,\"lowTime\":1}}}";
            }
            if (url.endsWith("/1h")) {
                return "{\"data\":{\"100\":{\"highPriceVolume\":5000,\"lowPriceVolume\":5000}}}";
            }
            throw new IllegalArgumentException("unexpected url: " + url);
        }
    }
}
