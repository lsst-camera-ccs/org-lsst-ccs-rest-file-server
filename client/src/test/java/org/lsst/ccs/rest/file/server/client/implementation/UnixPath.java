package org.lsst.ccs.rest.file.server.client.implementation;

import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractPath;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractPathBuilder;

/**
 *
 * @author tonyj
 */
class UnixPath extends AbstractPath {

    private final FileSystem fileSystem;


    private UnixPath(Builder builder, boolean absolute, List<String> path) {
        super(builder, absolute, path);
        this.fileSystem = FileSystems.getDefault();
    }

    private UnixPath(Builder builder, String path){
        super(builder, path);
        this.fileSystem = FileSystems.getDefault();
    }
    
    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public URI toUri() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    static class Builder implements AbstractPathBuilder {

        @Override
        public Path getPath(String first, String... more) {
            return new UnixPath(this, first + (more==null || more.length == 0 ? "" : "/" + String.join("/", more)));
        }

        @Override
        public Path getPath(boolean absolute, List<String> path) {
            return new UnixPath(this, absolute, path);
        }
        
    }
}
