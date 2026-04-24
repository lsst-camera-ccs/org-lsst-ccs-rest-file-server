package org.lsst.ccs.web.rest.file.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages hidden-entry metadata for a directory, persisted as a JSON set in a
 * {@value #HIDDEN_FILE_NAME} sidecar file.
 */
class DirectoryMetadata {

    static final String HIDDEN_FILE_NAME = ".hidden";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path dir;

    DirectoryMetadata(Path dir) {
        this.dir = dir;
    }

    Set<String> getHiddenNames() throws IOException {
        Path hiddenFile = dir.resolve(HIDDEN_FILE_NAME);
        if (!Files.exists(hiddenFile)) {
            return Collections.emptySet();
        }
        return OBJECT_MAPPER.readValue(hiddenFile.toFile(), new TypeReference<LinkedHashSet<String>>() {});
    }

    boolean isHidden(String name) throws IOException {
        return getHiddenNames().contains(name);
    }

    void setHidden(String name, boolean hidden) throws IOException {
        Set<String> names = new LinkedHashSet<>(getHiddenNames());
        boolean modified = hidden ? names.add(name) : names.remove(name);
        if (modified) {
            if (names.isEmpty()) {
                Files.deleteIfExists(dir.resolve(HIDDEN_FILE_NAME));
            } else {
                OBJECT_MAPPER.writeValue(dir.resolve(HIDDEN_FILE_NAME).toFile(), names);
            }
        }
    }
}
