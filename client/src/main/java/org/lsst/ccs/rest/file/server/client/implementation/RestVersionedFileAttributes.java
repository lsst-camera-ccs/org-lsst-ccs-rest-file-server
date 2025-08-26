package org.lsst.ccs.rest.file.server.client.implementation;

import java.nio.file.attribute.BasicFileAttributes;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.web.rest.file.server.data.VersionInfoV2;

/**
 * {@link VersionedFileAttributes} backed by {@link VersionInfoV2} metadata from
 * the REST service.
 */
class RestVersionedFileAttributes extends RestFileAttributes implements VersionedFileAttributes {

    private final VersionInfoV2 info;

    /**
     * Creates a new attribute view for a versioned file.
     *
     * @param info version information returned by the server
     */
    RestVersionedFileAttributes(VersionInfoV2 info) {
        super(info.getVersions().get(info.getDefault() - 1));
        this.info = info;
    }

    /** {@inheritDoc} */
    @Override
    public int[] getVersions() {
        return info.getVersions().stream().mapToInt(v -> v.getVersion()).toArray();
    }

    /** {@inheritDoc} */
    @Override
    public int getLatestVersion() {
        return info.getLatest();
    }

    /** {@inheritDoc} */
    @Override
    public int getDefaultVersion() {
        return info.getDefault();
    }

    /** {@inheritDoc} */
    @Override
    public BasicFileAttributes getAttributes(int version) {
        VersionInfoV2.Version versionAttributes = info.getVersions().get(version - 1);
        return new RestFileAttributes(versionAttributes);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHidden(int version) {
        VersionInfoV2.Version versionAttributes = info.getVersions().get(version - 1);
        return versionAttributes.isHidden();
    }

    /** {@inheritDoc} */
    @Override
    public String getComment(int version) {
        VersionInfoV2.Version versionAttributes = info.getVersions().get(version - 1);
        return versionAttributes.getComment();
    }
}
