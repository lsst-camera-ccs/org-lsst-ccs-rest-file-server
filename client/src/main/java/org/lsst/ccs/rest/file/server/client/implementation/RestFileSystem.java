package org.lsst.ccs.rest.file.server.client.implementation;

import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractFileSystem;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractPathBuilder;

/**
 *
 * @author tonyj
 */
public class RestFileSystem extends AbstractFileSystem implements AbstractPathBuilder {

    private static final Set<String> SUPPORTED_VIEWS = new HashSet<>();
    static {
        SUPPORTED_VIEWS.add("basic");
        SUPPORTED_VIEWS.add("versioned");
    }

    private final RestFileSystemProvider provider;
    private final URI uri;
    private final Map<String, ?> env;
    private final RestClient restClient;

    public RestFileSystem(RestFileSystemProvider provider, URI uri, Map<String, ?> env) throws IOException {
        this.provider = provider;
        this.uri = uri;
        this.env = env;
        Client client = ClientBuilder.newClient();
        Cache cache = new Cache();
        client.register(new CacheRequestFilter(cache));
        client.register(new CacheResponseFilter(cache));
        restClient = new RestClient(client, computeRestURI(client));
    }

    private URI computeRestURI(Client client) throws IOException {
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

    RestClient getClient() {
        return restClient;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        restClient.close();
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
    public Path getPath(String first, String... more) {
        if (more.length == 0) {
            return new RestPath(this, first);
        } else {
            return new RestPath(this, first + "/" + String.join("/", more));
        }
    }

    @Override
    public Path getPath(boolean isAbsolute, List<String> path) {
        return new RestPath(this, isAbsolute, path);
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

    private String getURLSchema() {
        Object useSSL = env.get("useSSL");
        return useSSL != null && Boolean.valueOf(useSSL.toString()) ? "https" : "http";
    }

    URI getURI(String path) {
        return uri.resolve(path);
    }

}
