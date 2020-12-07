package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author tonyj
 */
public class RestFileSystemProvider extends FileSystemProvider {

    private final Map<URI, RestFileSystem> cache = new ConcurrentHashMap<>();

    @Override
    public String getScheme() {
        return "ccs";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        synchronized (cache) {
            RestFileSystem result = cache.get(uri); 
            if (result == null) {
               result = new RestFileSystem(RestFileSystemProvider.this, uri, env);
               cache.put(uri, result);
            }
            return result;
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return cache.get(uri);
    }

    @Override
    public Path getPath(URI uri) {
        if (!getScheme().equals(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported scheme "+uri);
        }
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return toRestPath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return toRestPath(path).newOutputStream(options);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return toRestPath(dir).newDirectoryStream(filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        toRestPath(dir).createDirectory(attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        toRestPath(path).delete();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        toRestPath(source).move(toRestPath(target), options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        if (path == path2) return true;
        if (path.getFileSystem() != path2.getFileSystem()) return false;
        return toRestPath(path).isSameFile(toRestPath(path2));
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
        toRestPath(path).checkAccess(modes);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (type == BasicFileAttributeView.class) {
            return type.cast(toRestPath(path).getFileAttributeView());
        } else if (type == VersionedFileAttributeView.class) {
            return type.cast(toRestPath(path).getVersionedAttributeView());
        }
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class) {
            return type.cast(toRestPath(path).getAttributes());
        } else if (type == VersionedFileAttributes.class) {
            return type.cast(toRestPath(path).getVersionedAttributes());
        }
        return null;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return toRestPath(path).readAttributes(attributes);
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

}
