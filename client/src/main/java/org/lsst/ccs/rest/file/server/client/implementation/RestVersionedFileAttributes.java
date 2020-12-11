package org.lsst.ccs.rest.file.server.client.implementation;

import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.web.rest.file.server.data.VersionInfo;


/**
 *
 * @author tonyj
 */
class RestVersionedFileAttributes extends RestFileAttributes implements VersionedFileAttributes {

    private final VersionInfo info;

    RestVersionedFileAttributes(VersionInfo info) {
        super(info.getVersions().get(info.getDefault()-1));
        this.info = info;
    }

    @Override
    public int[] getVersions() {
        return info.getVersions().stream().mapToInt(v -> v.getVersion()).toArray();
    }

    @Override
    public int getLatestVersion() {
        return info.getLatest();
    }

    @Override
    public int getDefaultVersion() {
        return info.getDefault();
    }
    
}
