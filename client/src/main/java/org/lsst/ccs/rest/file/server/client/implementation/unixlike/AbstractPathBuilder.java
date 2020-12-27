package org.lsst.ccs.rest.file.server.client.implementation.unixlike;

import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author tonyj
 */
public interface AbstractPathBuilder {

    Path getPath(String first, String... more);
    Path getPath(boolean absolute, List<String> path);
}
