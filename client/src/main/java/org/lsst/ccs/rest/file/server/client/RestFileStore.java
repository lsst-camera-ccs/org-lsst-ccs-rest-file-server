package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 *
 * @author tonyj
 */
public class RestFileStore extends FileStore {

    private RestFileSystem fileSystem;

    RestFileStore(RestFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }
    
    @Override
    public String name() {
        return fileSystem.toString();
    }

    @Override
    public String type() {
        return "ccs";
    }

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

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
