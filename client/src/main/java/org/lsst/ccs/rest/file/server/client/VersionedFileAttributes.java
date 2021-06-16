package org.lsst.ccs.rest.file.server.client;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * Extra file attributes associated with versioned files
 * @author tonyj
 */
public interface VersionedFileAttributes extends BasicFileAttributes {

    int[] getVersions();

    int getLatestVersion();

    int getDefaultVersion();
    
    boolean isHidden(int version);
    
    String getComment(int version);
    
    BasicFileAttributes getAttributes(int version);
}
