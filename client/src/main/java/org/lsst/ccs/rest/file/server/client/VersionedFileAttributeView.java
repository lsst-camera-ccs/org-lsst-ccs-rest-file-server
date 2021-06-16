package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;

/**
 * A custom attribute view for use with versioned files.
 *
 * @author tonyj
 */
public interface VersionedFileAttributeView extends FileAttributeView {

    VersionedFileAttributes readAttributes() throws IOException;

    void setDefaultVersion(int version) throws IOException;
    
    void setComment(int version, String comment) throws IOException;
    
    void setHidden(int version, boolean hidden) throws IOException;
}
