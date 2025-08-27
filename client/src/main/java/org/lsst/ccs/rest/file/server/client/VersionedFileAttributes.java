package org.lsst.ccs.rest.file.server.client;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * Extra file attributes associated with versioned files.
 * Provides access to version metadata and per-version file attributes.
 *
 * @author tonyj
 */
public interface VersionedFileAttributes extends BasicFileAttributes {

    /**
     * Returns the list of version numbers available for the file.
     *
     * @return array of available version numbers
     */
    int[] getVersions();

    /**
     * Gets the most recent version number available for the file.
     *
     * @return latest version number
     */
    int getLatestVersion();

    /**
     * Gets the default version number for the file.
     *
     * @return default version number
     */
    int getDefaultVersion();

    /**
     * Checks whether a specific version is marked as hidden.
     *
     * @param version version number to test
     * @return {@code true} if the version is hidden
     */
    boolean isHidden(int version);

    /**
     * Retrieves the comment associated with a given version.
     *
     * @param version version number for which to obtain the comment
     * @return comment text or {@code null} if none exists
     */
    String getComment(int version);

    /**
     * Provides the basic file attributes for a particular version.
     *
     * @param version version number for which to retrieve attributes
     * @return basic attributes of the specified version
     */
    BasicFileAttributes getAttributes(int version);
}
