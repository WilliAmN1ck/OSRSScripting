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
import com.osrsscripts.core.ge.OfferTracker;
import com.osrsscripts.core.ge.StockLedger;
import com.osrsscripts.core.model.FlipConfig;
import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import com.osrsscripts.core.persistence.PersistedState;
import com.osrsscripts.core.prices.HttpFetcher;
import com.osrsscripts.core.prices.WikiPriceClient;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FlipTaskTest {

    private static final String BASE_URL = "http://test/api";

    private final FakeGeClient client = new FakeGeClient();
    private final FlipScanner scanner = new FlipScanner();
    private final FlipEngine engine = new FlipEngine();
    private final GeTax tax = new GeTax(GeTaxRules.defaults());
    private final BuyLimitLedger ledger = new BuyLimitLedger();
    private final StockLedger stock = new StockLedger();
    private final OfferTracker tracker = new OfferTracker(ledger, stock, tax);
    private final AtomicReference<FlipConfig> config = new AtomicReference<>(buyingConfig());
    private final List<PersistedState> persisted = new ArrayList<>();

    private FlipTask task(HttpFetcher fetcher) {
        return task(fetcher, Duration.ZERO);
    }

    private FlipTask task(HttpFetcher fetcher, Duration retryBackoff) {
        WikiPriceClient prices = new WikiPriceClient(
                fetcher, Clock.systemUTC(), BASE_URL, Duration.ofMinutes(5), Duration.ofHours(6));
        return new FlipTask(client, prices, scanner, engine, tax, ledger, stock, tracker,
                config::get, new FlipActionExecutor(client), persisted::add, retryBackoff);
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
    void opensTheGeOnlyWhenActionsArePending() {
        client.open = false;
        client.coins = 1_000L;
        client.offers = OfferMapper.fillEightSlots(List.of());

        FlipTask task = task(new CannedFetcher());
        task.execute();

        assertEquals(1, client.openCalls, "pending buy: open the GE first");
        assertTrue(client.buys.isEmpty(), "placement waits until the GE is open");

        client.open = true;
        task.execute();
        assertEquals(1, client.buys.size(), "next tick places through the open GE");
    }

    @Test
    void idleTickNeverTouchesTheGe() {
        client.open = false;
        client.coins = 0L; // nothing affordable, nothing owned: no actions
        client.offers = OfferMapper.fillEightSlots(List.of());

        task(new CannedFetcher()).execute();

        assertEquals(0, client.openCalls, "no work, no interface churn");
    }

    @Test
    void failedGeOpenBacksOffInsteadOfRetryingEveryTick() {
        client.open = false;
        client.opensSucceed = false;
        client.coins = 1_000L;
        client.offers = OfferMapper.fillEightSlots(List.of());

        FlipTask task = task(new CannedFetcher(), Duration.ofHours(1));
        task.execute();
        assertEquals(1, client.openCalls);

        task.execute();
        assertEquals(1, client.openCalls, "backoff: no immediate open retry");
    }

    @Test
    void failedPlacementBacksOffInsteadOfFlapping() {
        client.open = true;
        client.coins = 1_000L;
        client.offers = OfferMapper.fillEightSlots(List.of());
        client.placementsSucceed = false;

        FlipTask task = task(new CannedFetcher(), Duration.ofHours(1));
        task.execute();
        assertEquals(1, client.buys.size());
        assertEquals(1, client.closeCalls, "failed placement resets the interface");

        task.execute();
        assertEquals(1, client.buys.size(), "backoff: no immediate retry");
        assertEquals(1, client.closeCalls);
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

    @Test
    void staleTrackedOfferIsCancelled() {
        client.open = true;
        client.coins = 0L;
        GeOffer live = new GeOffer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100, 100L, 10, 0, null);
        client.offers = OfferMapper.fillEightSlots(List.of(live));

        // The tracker remembers this offer being placed an hour ago; maxOfferAge is 30 min.
        tracker.restore(List.of(new OfferTracker.Stamp(1, 100, OfferSide.BUY, 100L, 0,
                Instant.now().minus(Duration.ofHours(1)))), 0L, 0L);

        task(new CannedFetcher()).execute();

        assertEquals(List.of(1), client.aborts);
    }

    @Test
    void freshOfferIsNotCancelled() {
        client.open = true;
        client.coins = 0L;
        GeOffer live = new GeOffer(1, OfferStatus.ACTIVE, OfferSide.BUY, 100, 100L, 10, 0, null);
        client.offers = OfferMapper.fillEightSlots(List.of(live));

        task(new CannedFetcher()).execute(); // first sight: stamped now

        assertTrue(client.aborts.isEmpty());
    }

    @Test
    void onlyFlipperBoughtStockIsSold() {
        client.open = true;
        client.coins = 0L;
        client.offers = OfferMapper.fillEightSlots(List.of());
        client.stock.put(100, 5); // in inventory…

        task(new CannedFetcher()).execute();
        assertTrue(client.sells.isEmpty(), "pre-owned items are never offered");

        stock.recordBuy(100, 3, 100L); // …but only 3 were bought by the flipper
        task(new CannedFetcher()).execute();

        assertEquals(1, client.sells.size());
        assertArrayEquals(new int[] {100, 200, 3}, client.sells.get(0));
    }

    @Test
    void persisterRunsOnlyOnTicksThatActed() {
        client.open = true;
        client.coins = 0L; // nothing affordable, nothing owned: no actions
        client.offers = OfferMapper.fillEightSlots(List.of());

        FlipTask task = task(new CannedFetcher());
        task.execute();
        assertTrue(persisted.isEmpty(), "no actions, no save");

        client.coins = 1_000L; // now a buy happens
        task.execute();
        assertEquals(1, persisted.size());
    }

    @Test
    void configChangesApplyOnTheNextTick() {
        client.open = true;
        client.coins = 1_000L;
        client.offers = OfferMapper.fillEightSlots(List.of());

        // Margin bar nobody clears: no buys.
        config.set(FlipConfig.builder()
                .capitalCap(1_000_000L).perItemCapitalCap(1_000_000L)
                .minMarginGp(1_000_000L).minMarginPct(0.0).minVolume(0L)
                .maxSlots(8).maxOfferAge(Duration.ofMinutes(30)).build());
        FlipTask task = task(new CannedFetcher());
        task.execute();
        assertTrue(client.buys.isEmpty());

        config.set(buyingConfig());
        task.execute();
        assertEquals(1, client.buys.size());
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
