package org.lsst.ccs.rest.file.server.client.implementation.unixlike;

import java.nio.file.Path;
import java.util.List;

/**
 * Factory used by {@link AbstractPath} implementations to create new path
 * instances.
 */
public interface AbstractPathBuilder {

    /**
     * Builds a path from the supplied elements.
     *
     * @param first the first element of the path
     * @param more additional elements
     * @return constructed path
     */
    Path getPath(String first, String... more);

    /**
     * Builds a path from a list of elements.
     *
     * @param absolute whether the path is absolute
     * @param path elements that form the path
     * @return constructed path
     */
    Path getPath(boolean absolute, List<String> path);
}
