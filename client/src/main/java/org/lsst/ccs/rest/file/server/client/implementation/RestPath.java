package org.lsst.ccs.rest.file.server.client.implementation;

import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractPath;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.List;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;

/**
 * Concrete {@link java.nio.file.Path} implementation used by
 * {@link RestFileSystem}.
 */
class RestPath extends AbstractPath {

    private final boolean isReadOnly;
    private final RestFileInfo presetInfo;
    private Boolean isVersionedFile;
//    
//    private RestPath(RestFileSystem fileSystem, List<String> path, boolean isReadOnly, boolean isAbsolute, RestFileInfo info) {
//        this.fileSystem = fileSystem;
//        this.path = new LinkedList<>(path);
//        this.isReadOnly = isReadOnly;
//        this.isAbsolute = isAbsolute;
//        this.presetInfo = info;
//        this.isVersionedFile = info.isVersionedFile();
//    }
    private RestFileSystem fileSystem;
    private String version;
    private String pathWithoutVersion;

    RestPath(RestFileSystem fileSystem, String path) {
        this(fileSystem, new VersionedPathCheck(path));
    }
    
    private RestPath(RestFileSystem fileSystem, VersionedPathCheck path) {
        super(fileSystem, path.getOriginalPath());
        this.version = path.getVersion();
        this.pathWithoutVersion = path.getPathWithVersionRemoved();
        this.fileSystem = fileSystem;
        this.isReadOnly = false;
        this.presetInfo = null;
    }

    RestPath(RestFileSystem fileSystem, boolean absolute, List<String> path) {
        this(fileSystem, fullPath(path, absolute));
    }

    private static String fullPath(List<String> path, boolean absolute) {
        String result = "";
        int count = 0;
        for ( String p : path ) {
            count++;
            result += p;
            if ( count < path.size() ) {
                result += DELIMETER;
            }
            
        }
        return absolute ? DELIMETER+result : result;
    }
        
    synchronized boolean isVersionedFile() throws IOException {
        if (isVersionedFile == null) {
            if ( version != null ) {
                isVersionedFile = true;
            } else {
                RestFileInfo info = getClient().getRestFileInfo(this);
                isVersionedFile = info.isVersionedFile();
            }
        }
        return isVersionedFile != null && isVersionedFile;
    }
    
    String getVersion() {
        return version;
    }

    String getRestPath() {
        //return toAbsolutePath().toString().substring(1);
        return isAbsolute() ? pathWithoutVersion.substring(1) : pathWithoutVersion;
    }

    RestClient getClient() {
        return ((RestFileSystem) this.getFileSystem()).getClient();
    }

    /** {@inheritDoc} */
    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    /** {@inheritDoc} */
    @Override
    public URI toUri() {
        return fileSystem.getURI(this.toAbsolutePath().toString().substring(1));
    }
}
