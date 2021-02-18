package org.lsst.ccs.rest.file.server.client.implementation;

import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractPath;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;
import java.util.List;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;

/**
 *
 * @author tonyj
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

    RestPath(RestFileSystem fileSystem, String path) {
        super(fileSystem, path);
        this.fileSystem = fileSystem;
        this.isReadOnly = false;
        this.presetInfo = null;
    }

    RestPath(RestFileSystem fileSystem, boolean absolute, List<String> path) {
        super(fileSystem, absolute, path);
        this.fileSystem = fileSystem;
        this.isReadOnly = false;
        this.presetInfo = null;
    }

    synchronized boolean isVersionedFile() throws IOException {
        if (isVersionedFile == null) {
            RestFileInfo info = getClient().getRestFileInfo(this);
            isVersionedFile = info.isVersionedFile();
        }
        return isVersionedFile != null && isVersionedFile;
    }

    String getRestPath() {
        return toAbsolutePath().toString().substring(1);
    }

    RestClient getClient() {
        return ((RestFileSystem) this.getFileSystem()).getClient();
    }

    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public URI toUri() {
        return fileSystem.getURI(this.toAbsolutePath().toString().substring(1));
    }
}
