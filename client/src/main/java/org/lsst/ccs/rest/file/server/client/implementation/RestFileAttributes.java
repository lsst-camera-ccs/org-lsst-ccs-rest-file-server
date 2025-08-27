package org.lsst.ccs.rest.file.server.client.implementation;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;

/**
 * Implementation of {@link BasicFileAttributes} backed by
 * {@link RestFileInfo} data retrieved from the REST service.
 */
class RestFileAttributes implements BasicFileAttributes {

    private final RestFileInfo info;
    
    /**
     * Creates a new set of file attributes.
     *
     * @param info metadata describing the file; must not be {@code null}
     */
    RestFileAttributes(RestFileInfo info) {
        if (info == null) throw new NullPointerException();
        this.info = info;
    }

    /** {@inheritDoc} */
    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(info.getLastModified());
    }

    /** {@inheritDoc} */
    @Override
    public FileTime lastAccessTime() {
        return FileTime.fromMillis(info.getLastAccessTime());
    }

    /** {@inheritDoc} */
    @Override
    public FileTime creationTime() {
        return FileTime.fromMillis(info.getCreationTime());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRegularFile() {
        return info.isRegularFile() && !info.isVersionedFile();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDirectory() {
        return info.isDirectory();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isSymbolicLink() {
        return info.isSymbolicLink();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOther() {
        return info.isOther() || info.isVersionedFile();
    }

    /** {@inheritDoc} */
    @Override
    public long size() {
        return info.getSize();
    }

    /** {@inheritDoc} */
    @Override
    public Object fileKey() {
        return info.getFileKey();
    }
    
}
