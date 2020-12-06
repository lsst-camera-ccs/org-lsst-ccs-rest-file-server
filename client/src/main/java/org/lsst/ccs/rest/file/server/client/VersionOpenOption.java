package org.lsst.ccs.rest.file.server.client;

import java.nio.file.OpenOption;

/**
 *
 * @author tonyj
 */
public enum VersionOpenOption implements OpenOption {
    CREATE,
    UPDATE,
    CREATE_OR_UPDATE
}
