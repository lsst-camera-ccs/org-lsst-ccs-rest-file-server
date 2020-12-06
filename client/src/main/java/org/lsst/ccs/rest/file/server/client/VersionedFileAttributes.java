package org.lsst.ccs.rest.file.server.client;

/**
 * Extra file attributes associated with versioned files
 * @author tonyj
 */
public interface VersionedFileAttributes {

    int[] getVersions();

    int getLatestVersion();

    int getDefaultVersion();
}
