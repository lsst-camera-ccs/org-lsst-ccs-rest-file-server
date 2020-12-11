package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author tonyj
 */
public class RestFileSystem extends FileSystem {

    private final RestFileSystemProvider provider;
    private final URI uri;
    private final RestPath rootPath = new RestPath(this, "/", false);
    private final Map<String, ?> env;
    private final URI restURI;
    private final Client client = ClientBuilder.newClient();
    private static final Set<String> SUPPORTED_VIEWS = new HashSet<>();
    static {
        SUPPORTED_VIEWS.add("basic");
        SUPPORTED_VIEWS.add("versioned");
    }
 
    public RestFileSystem(RestFileSystemProvider provider, URI uri, Map<String, ?> env) throws IOException {
        this.provider = provider;
        this.uri = uri;
        this.env = env;
        this.restURI = computeRestURI();
    }

    private URI computeRestURI() throws IOException {
        // Test if we can connect, handle redirects
        URI trialRestURI = UriBuilder.fromUri(uri).scheme(getURLSchema()).build();
        URI testURI = trialRestURI.resolve("rest/list/");
        Response response = client.target(testURI).request(MediaType.APPLICATION_JSON).head();
        if (response.getStatus() / 100 == 3) {
            String location = response.getHeaderString("Location");
            testURI = UriBuilder.fromUri(location).build();
            response = client.target(testURI).request(MediaType.APPLICATION_JSON).head();
            if (response.getStatus() != 200) {
                throw new IOException("Cannot create rest file system, rc=" + response.getStatus());
            }
            trialRestURI = testURI.resolve("../..");
        } else if (response.getStatus() != 200) {
            throw new IOException("Cannot create rest file system, rc=" + response.getStatus());
        }
        return trialRestURI;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        client.close();
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
        return SUPPORTED_VIEWS;
    }

    @Override
    public RestPath getPath(String first, String... more) {
        if (more.length == 0) {
            return new RestPath(this, first, false);
        } else {
            return new RestPath(this, first + "/" + String.join("/", more), false);
        }
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    URI getRestURI(String restPath, List<String> filePath) throws IOException {
        return restURI.resolve(restPath).resolve(String.join("/", filePath));
    }
    
    WebTarget getRestTarget(String restPath, List<String> filePath) throws IOException {
        return client.target(getRestURI(restPath, filePath));
    }

    WebTarget getRestTarget(URI uri) {
        return client.target(uri);
    }
    
    URI getURI(List<String> filePath) {
        return uri.resolve(String.join("/", filePath));
    }

    private String getURLSchema() {
        Object useSSL = env.get("useSSL");
        return useSSL != null && Boolean.valueOf(useSSL.toString()) ? "https" : "http";
    }

}
