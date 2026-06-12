package com.osrsscripts.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                123_456L, 7L);

        store.save(original);

        assertEquals(original, store.load());
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
