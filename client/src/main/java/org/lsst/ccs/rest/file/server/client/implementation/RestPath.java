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

    RestPath(RestFileSystem fileSystem, String path) {
        this(fileSystem, new VersionedPathCheck(path));
    }
    
    private RestPath(RestFileSystem fileSystem, VersionedPathCheck path) {
        super(fileSystem, path.getPathWithVersionRemoved());
        this.version = path.getVersion();
        this.fileSystem = fileSystem;
        this.isReadOnly = false;
        this.presetInfo = null;
    }

    RestPath(RestFileSystem fileSystem, boolean absolute, List<String> path) {
        super(fileSystem, absolute, path);
        this.version = null;
        this.fileSystem = fileSystem;
        this.isReadOnly = false;
        this.presetInfo = null;
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
        return toAbsolutePath().toString().substring(1);
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
