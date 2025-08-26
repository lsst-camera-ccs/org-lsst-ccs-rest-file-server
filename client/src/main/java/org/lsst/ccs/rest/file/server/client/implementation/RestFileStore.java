package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 * {@link FileStore} representation for the REST file system. Most space related
 * queries are not supported and will throw {@link UnsupportedOperationException}.
 */
class RestFileStore extends FileStore {

    private final RestFileSystem fileSystem;

    /**
     * Creates a new file store backed by the given file system.
     *
     * @param fileSystem the owning file system
     */
    RestFileStore(RestFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    /** {@inheritDoc} */
    @Override
    public String name() {
        return fileSystem.toString();
    }

    /** {@inheritDoc} */
    @Override
    public String type() {
        return "ccs";
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReadOnly() {
        return fileSystem.isReadOnly();
    }

    @Override
    public long getTotalSpace() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getUsableSpace() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return type.isAssignableFrom(BasicFileAttributeView.class) || type.isAssignableFrom(RestVersionedFileAttributes.class);
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsFileAttributeView(String name) {
        return fileSystem.supportedFileAttributeViews().contains(name);
    }

    /** {@inheritDoc} */
    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String attribute) throws IOException {
        return null;
    }
    
}
