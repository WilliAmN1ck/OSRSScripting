package com.osrsscripts.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateStoreTest {

    @Test
    void roundTripsState(@TempDir Path dir) throws IOException {
        StateStore store = new StateStore(dir.resolve("state.json"));
        PersistedState original = new PersistedState(
                Arrays.asList(new LedgerEntry(561, 100, 1_700_000_000_000L),
                        new LedgerEntry(4151, 1, 1_700_000_500_000L)),
                Arrays.asList(new StockEntry(4151, 3, 100_000L)),
                Arrays.asList(new OfferStampEntry(1, 4151, "SELL", 150_000L, 2, 300_000L,
                        1_700_000_600_000L)),
                Arrays.asList(new TradeRecordEntry(4151, 470L, 1, 10, 1_700_000_700_000L)),
                new PersistedConfig(116_000L, 25_000L, 2L, 0.01, 5_000L, 3, 30L, false, 1_000L, 3,
                        2_000L),
                123_456L, 7L);

        store.save(original);

        assertEquals(original, store.load());
    }

    @Test
    void v2FileWithoutConfigLoadsNullConfig(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("state.json");
        Files.write(file, ("{\"ledgerEntries\":[],\"stockEntries\":[],\"offerStamps\":[],"
                + "\"realizedProfit\":42,\"flipsCompleted\":3}").getBytes());

        PersistedState loaded = new StateStore(file).load();

        assertEquals(42L, loaded.realizedProfit());
        assertEquals(null, loaded.config());
    }

    @Test
    void v1FileWithoutNewFieldsLoadsThemEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("state.json");
        // The 3c-era schema before stockEntries/offerStamps existed.
        Files.write(file, ("{\"ledgerEntries\":[{\"itemId\":561,\"qty\":100,"
                + "\"epochMillis\":1700000000000}],"
                + "\"realizedProfit\":42,\"flipsCompleted\":3}").getBytes());

        PersistedState loaded = new StateStore(file).load();

        assertEquals(1, loaded.ledgerEntries().size());
        assertEquals(42L, loaded.realizedProfit());
        assertEquals(3L, loaded.flipsCompleted());
        assertTrue(loaded.stockEntries().isEmpty());
        assertTrue(loaded.offerStamps().isEmpty());
    }

    @Test
    void nullListElementsAreDroppedOnLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("state.json");
        // Valid JSON, semantically broken: must fail safe, not NPE later during restore.
        Files.write(file, ("{\"ledgerEntries\":[null],\"stockEntries\":[null,"
                + "{\"itemId\":4151,\"qty\":3,\"pricePerItem\":100}],"
                + "\"offerStamps\":[null],\"realizedProfit\":0,\"flipsCompleted\":0}").getBytes());

        PersistedState loaded = new StateStore(file).load();

        assertTrue(loaded.ledgerEntries().isEmpty());
        assertTrue(loaded.offerStamps().isEmpty());
        assertEquals(1, loaded.stockEntries().size());
    }

    @Test
    void missingFileLoadsEmpty(@TempDir Path dir) {
        StateStore store = new StateStore(dir.resolve("does-not-exist.json"));
        assertEquals(PersistedState.empty(), store.load());
    }

    @Test
    void corruptFileLoadsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("state.json");
        Files.write(file, "{ not valid json".getBytes());
        StateStore store = new StateStore(file);
        assertEquals(PersistedState.empty(), store.load());
    }
}
