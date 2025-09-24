package org.lsst.ccs.rest.file.server.client.implementation;

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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;
import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractPathBuilder;
import org.lsst.ccs.rest.file.server.client.implementation.unixlike.AbstractFileSystem;

/**
 * {@link java.nio.file.FileSystem} implementation that communicates with a
 * remote REST file server.
 */
public class RestFileSystem extends AbstractFileSystem implements AbstractPathBuilder {

    private static final Set<String> SUPPORTED_VIEWS = new HashSet<>();

    static {
        SUPPORTED_VIEWS.add("basic");
        SUPPORTED_VIEWS.add("versioned");
    }

    private final RestFileSystemProvider provider;
    private final URI uri;
    private final RestFileSystemOptionsHelper options;
    private final RestClient restClient;
    private boolean offline = false;
    private static final Logger LOG = Logger.getLogger(RestFileSystem.class.getName());
    private final URI mountPoint;
    private final CacheResponseFilter cacheResponseFilter;

    /**
     * Creates a new REST-based file system.
     *
     * @param provider owning provider instance
     * @param uri base URI of the REST server
     * @param env configuration options controlling client behaviour
     * @throws IOException if the system cannot be initialised
     */
    public RestFileSystem(RestFileSystemProvider provider, URI uri, Map<String, ?> env) throws IOException {
        this.provider = provider;
        uri = uri.getPath().endsWith("/") ? uri : UriBuilder.fromUri(uri).path(uri.getPath()+"/").build();
        this.options = new RestFileSystemOptionsHelper(env);
        mountPoint = options.getMountPoint();
        String cacheRegion = "default";
        if ( !mountPoint.getPath().equals(".") ) {
            cacheRegion = mountPoint.getPath().replaceAll("/", "");
        }
        this.uri = uri;
        Client client = ClientBuilder.newBuilder().readTimeout(3, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS).build();
        final URI restURI = computeRestURI(client);        
        if (options.getCacheOptions() != RestFileSystemOptions.CacheOptions.NONE) {            
            CacheBuilder.addRegion(cacheRegion, options);
            client.register(new CacheRequestFilter(cacheRegion, offline || options.getCacheFallback() == RestFileSystemOptions.CacheFallback.ALWAYS));
            this.cacheResponseFilter = new CacheResponseFilter(cacheRegion, getExpireCacheEntry(options.getCacheFallback()));
            client.register(cacheResponseFilter);
        } else {
            cacheResponseFilter = null;
        }
        client.register(new AddProtcolVersionRequestFilter());
        Object jwt = env.get(RestFileSystemOptions.AUTH_TOKEN);
        if (jwt != null) {
            client.register(new AddJWTTokenRequestFilter(jwt.toString()));
        }
        restClient = new RestClient(client, restURI, mountPoint);
    }

    private URI computeRestURI(Client client) throws IOException {
        RestFileSystemOptions.SSLOptions useSSL = options.isUseSSL();
        String schema = useSSL == RestFileSystemOptions.SSLOptions.TRUE ? "https" : "http";
        // Test if we can connect, handle redirects
        URI trialRestURI = UriBuilder.fromUri(uri).scheme(schema).build();
        if (useSSL == RestFileSystemOptions.SSLOptions.AUTO || options.getCacheFallback() == RestFileSystemOptions.CacheFallback.OFFLINE) {
                URI testURI = trialRestURI.resolve("rest/list/");
                try {
                    Response response = client.target(testURI).request(MediaType.APPLICATION_JSON).head();
                    if (response.getStatus() / 100 == 3) {
                        String location = response.getHeaderString("Location");
                        testURI = UriBuilder.fromUri(location).build();
                        response = client.target(testURI).request(MediaType.APPLICATION_JSON).head();
                        if (response.getStatus() != 200) {
                            throw new IOException("Cannot create rest file system, rc=" + response.getStatus() + "uri="+testURI);
                        }
                        trialRestURI = testURI.resolve("../..");
                    } else if (response.getStatus() != 200) {
                        throw new IOException("Cannot create rest file system, rc=" + response.getStatus() + "uri="+testURI);
                    }
            } catch (ProcessingException | IOException x) {
                    if (options.getCacheOptions() == RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK
                            && options.getCacheFallback() != RestFileSystemOptions.CacheFallback.NEVER) {
                        offline = true;
                        LOG.log(Level.WARNING, () -> String.format("Rest File server running in offline mode: %s (%s)", uri, x.getMessage()));
                    } else if (x instanceof ProcessingException) {
                        throw RestClient.convertProcessingException((ProcessingException) x);
                    } else {
                        throw x;
                    }
                }
            }
        return trialRestURI;
    }
    
    Cache getCache() {
        return CacheBuilder.getCache();
    }
    
    RestClient getClient() {
        return restClient;
    }

    private boolean getExpireCacheEntry(RestFileSystemOptions.CacheFallback cacheFallback) {
        return cacheFallback != RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE;        
    }
    
    void setCacheFallbackOption(RestFileSystemOptions.CacheFallback cacheFallback) {
        cacheResponseFilter.setExpireCacheEntry(getExpireCacheEntry(cacheFallback));
    }
    
    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        provider.dispose(getFullURI());
        restClient.close();
        if (cacheResponseFilter != null) {
            CacheBuilder.getCache().close();
        }
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

    URI getURI(String path) {
        return uri.resolve(path);
    }
    
    /**
     * Returns the URI path that is considered the root within the remote
     * server.
     *
     * @return the mount point URI
     */
    public URI getMountPoint() {
        return mountPoint;
    }
    
    URI getFullURI() {
        return uri.resolve(mountPoint);
    }
    
    static URI getFullURI(URI uri, Map<String,?> env) {
        RestFileSystemOptionsHelper optionsHelper = new RestFileSystemOptionsHelper(env);        
        URI restURI = uri.resolve(optionsHelper.getMountPoint());
        return restURI;
    }
    
}
