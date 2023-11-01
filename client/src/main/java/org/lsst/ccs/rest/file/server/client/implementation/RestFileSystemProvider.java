package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.utilities.misc.ExtendableURLStreamHandlerFactory;

/**
 * Implementation of FileSystemProvider for a CCS rest file ser
 *
 * @author tonyj
 */
public class RestFileSystemProvider extends FileSystemProvider {

    private final static Map<String, Object> NO_ENV = Collections.<String, Object>emptyMap();
    private static Map<String, ?> defaultEnvironment;

    private final Map<URI, RestFileSystem> cache = new ConcurrentHashMap<>();

    @Override
    public String getScheme() {
        return "ccs";
    }

    public static void setDefaultFileSystemOption(Map<String, ?> defaultEnv) {
        defaultEnvironment = defaultEnv;
    }
    
    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        if (env == null) {
            env = defaultEnvironment == null ? NO_ENV : defaultEnvironment;
        }
        
        URI restURI = RestFileSystem.getFullURI(uri, env);

        synchronized (cache) {
            RestFileSystem result = cache.get(restURI);
            if (result == null) {
                result = new RestFileSystem(RestFileSystemProvider.this, uri, env);
                cache.put(restURI, result);
                //See https://jira.slac.stanford.edu/browse/LSSTCCS-2796 ; also LSSTCCS-2836
                try {
                    ExtendableURLStreamHandlerFactory.add(new CCSCustomStreamHandlerFactory());
                } catch (IllegalStateException e) {
                    //In case the protocol is registered twice.
                }
                return result;
            }
            throw new FileSystemAlreadyExistsException(uri.toString());
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return cache.get(uri);
    }

    @Override
    public Path getPath(URI uri) {
        if (!getScheme().equals(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported scheme " + uri);
        }
        // Unfortunately we have no reliable way to tell where the rest server URL ends, and the file path starts
        // so we use some hueristics to try to figure it out
        for (Map.Entry<URI, RestFileSystem> entry : cache.entrySet()) {
            URI existingURI = entry.getKey();
            if (uri.toString().startsWith(existingURI.toString())) {
                URI relativeURI = existingURI.relativize(uri);
                return entry.getValue().getPath(relativeURI.toString());
            }
        }
        List<String> path = Arrays.asList(uri.getPath().substring(1).split("/"));
        for (int i = 0; i < path.size(); i++) {
            URI trialURI = UriBuilder.fromUri(uri).replacePath(String.join("/", path.subList(0, i)) + "/").build();
            try {
                FileSystem rfs = newFileSystem(trialURI, null);
                return rfs.getPath(path.get(i), String.join("/", path.subList(i + 1, path.size())));
            } catch (IOException x) {
                // OK, just carry on
            }
        }
        throw new FileSystemNotFoundException("Cannot create path for " + uri);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        final RestPath restPath = toRestPath(path);
        return restPath.getClient().newInputStream(restPath, options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        final RestPath restPath = toRestPath(path);
        return restPath.getClient().newOutputStream(restPath, options);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        final RestPath restPath = toRestPath(dir);
        return restPath.getClient().newDirectoryStream(restPath, filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        final RestPath restPath = toRestPath(dir);
        restPath.getClient().createDirectory(restPath, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        final RestPath restPath = toRestPath(path);
        restPath.getClient().delete(restPath);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        final RestPath sourcePath = toRestPath(source);
        sourcePath.getClient().move(sourcePath, toRestPath(target), options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        if (path == path2) {
            return true;
        }
        if (path.getFileSystem() != path2.getFileSystem()) {
            return false;
        }
        return path.toAbsolutePath().equals(path2.toAbsolutePath());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        final RestPath restPath = toRestPath(path);
        restPath.getClient().checkAccess(restPath, modes);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        final RestPath restPath = toRestPath(path);
        if (type == BasicFileAttributeView.class) {
            return type.cast(restPath.getClient().getFileAttributeView(restPath, options));
        } else if (type == VersionedFileAttributeView.class) {
            return type.cast(restPath.getClient().getVersionedAttributeView(restPath, options));
        }
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        final RestPath restPath = toRestPath(path);
        if (type == BasicFileAttributes.class) {
            return type.cast(restPath.getClient().getAttributes(restPath, options));
        } else if (type == VersionedFileAttributes.class) {
            return type.cast(restPath.getClient().getVersionedAttributes(restPath, options));
        }
        return null;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        final RestPath restPath = toRestPath(path);
        return restPath.getClient().readAttributes(restPath, attributes, options);
    }

    private RestPath toRestPath(Path path) {
        if (path == null) {
            throw new NullPointerException();
        }
        if (!(path instanceof RestPath)) {
            throw new ProviderMismatchException();
        }
        return (RestPath) path;
    }

    void dispose(URI uri) {
        if ( cache.remove(uri) == null ) {
            throw new RuntimeException("Something went wrong when closing file system "+uri+". The cache was not cleared and the possible values are: "+cache.keySet());
        }
    }
    
    public static class CCSCustomStreamHandlerFactory implements URLStreamHandlerFactory {

        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ( protocol.equals("ccs") ) {
                return new CCSCustomURLStreamHandler();
                
            }
            return null;
        }
        
    }
    
    public static class CCSCustomURLStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new CCSCustomURLConnection(u);
        }
        
    }
    
    public static class CCSCustomURLConnection extends URLConnection {

        CCSCustomURLConnection(URL u) {
            super(u);
        }
        
        @Override
        public void connect() throws IOException {
            //TO-DO: Implement this method.
        }
        
    }
    
    
    

}
