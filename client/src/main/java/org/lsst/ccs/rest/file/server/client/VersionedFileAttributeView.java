package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;

/**
 * A custom attribute view for use with versioned files.
 *
 * @author tonyj
 */
public interface VersionedFileAttributeView extends FileAttributeView {

    /**
     * Reads the extended versioned attributes for a file.
     *
     * @return file attributes including version information
     * @throws IOException if the attributes cannot be read
     */
    VersionedFileAttributes readAttributes() throws IOException;

    /**
     * Sets the default version for the file.
     *
     * @param version new default version number
     * @throws IOException if the default version cannot be updated
     */
    void setDefaultVersion(int version) throws IOException;

    /**
     * Associates a comment with a specific version.
     *
     * @param version version number to annotate
     * @param comment text to associate with the version
     * @throws IOException if the comment cannot be saved
     */
    void setComment(int version, String comment) throws IOException;

    /**
     * Marks a specific version as hidden or visible.
     *
     * @param version version number to modify
     * @param hidden {@code true} to hide the version
     * @throws IOException if the hidden flag cannot be changed
     */
    void setHidden(int version, boolean hidden) throws IOException;
}
