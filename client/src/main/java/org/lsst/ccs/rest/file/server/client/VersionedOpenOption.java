package org.lsst.ccs.rest.file.server.client;

import java.nio.file.OpenOption;

/**
 * Additional open options for use with versioned files.
 *
 * @author tonyj
 */
public enum VersionedOpenOption implements OpenOption {
   /** Open a version file diff. */
   DIFF
}
