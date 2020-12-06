package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author tonyj
 */
public class RestFileSystem extends FileSystem {

    private final RestFileSystemProvider provider;
    private final URI uri;
    private final RestPath rootPath = new RestPath(this, "/", false);
    private final Map<String, ?> env;

    public RestFileSystem(RestFileSystemProvider provider, URI uri, Map<String, ?> env) {
        this.provider = provider;
        this.uri = uri;
        this.env = env;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(rootPath);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.singletonList(new RestFileStore(this));
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Path getPath(String first, String... more) {
        if (more.length == 0) {
            return new RestPath(this, first, false);
        } else {
            return new RestPath(this, first + "/" + String.join("/", more), false);
        }
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    URI getURI(String restPath, LinkedList<String> filePath) throws IOException {
        return getURI(restPath, filePath, null);
    }

    URI getURI(String restPath, LinkedList<String> filePath, String query) throws IOException {
        try {
            URI result = uri.resolve(restPath);
            result = result.resolve(String.join("/", filePath));
            return new URI(getURLSchema(), null, result.getHost(), result.getPort(), result.getPath(), query, result.getFragment());
        } catch (URISyntaxException x) {
            throw new IOException("invalid URL", x);
        }
    }

    private String getURLSchema() {
        Object useSSL = env.get("useSSL");
        return useSSL != null && Boolean.valueOf(useSSL.toString())  ? "https" : "http";
    }

}
