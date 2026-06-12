package com.osrsscripts.core.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes {@link PersistedState} as JSON on disk. Writes are atomic (temp file then
 * rename) so a crash mid-write cannot corrupt the file; a missing or corrupt file loads as
 * {@link PersistedState#empty()} rather than throwing.
 */
public final class StateStore {

    private final Path file;
    private final ObjectMapper mapper;

    public StateStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper();
        // Bind by fields so the immutable value types need no getter-name conventions.
        this.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        this.mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        this.mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    public void save(PersistedState state) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, "state", ".tmp");
        try {
            mapper.writeValue(temp.toFile(), state);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // On success the temp was renamed away; on failure this cleans it up.
            Files.deleteIfExists(temp);
        }
    }

    /** Loads state, returning {@link PersistedState#empty()} if the file is missing or unreadable. */
    public PersistedState load() {
        try {
            if (!Files.exists(file)) {
                return PersistedState.empty();
            }
            return mapper.readValue(file.toFile(), PersistedState.class);
        } catch (IOException e) {
            return PersistedState.empty();
        }
    }
}
