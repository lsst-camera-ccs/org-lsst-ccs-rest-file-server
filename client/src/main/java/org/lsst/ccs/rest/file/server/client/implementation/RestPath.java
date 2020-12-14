package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.web.rest.file.server.data.IOExceptionResponse;
import org.lsst.ccs.web.rest.file.server.data.VersionInfo;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;

/**
 *
 * @author tonyj
 */
class RestPath extends AbstractPath {

    private final static LinkedList<String> EMPTY_PATH = new LinkedList<>();
    private final RestFileSystem fileSystem;
    private final LinkedList<String> path;
    private final boolean isReadOnly;
    private final boolean isAbsolute;
    private final RestFileInfo presetInfo;
    private Boolean isVersionedFile;

    RestPath(RestFileSystem fileSystem, String path, boolean isReadOnly) {
        this.fileSystem = fileSystem;
        this.isAbsolute = path.startsWith("/");
        if (this.isAbsolute) {
            path = path.substring(1);
        }
        this.path = path.isEmpty() ? EMPTY_PATH : new LinkedList<>(Arrays.asList(path.split("/")));
        this.isReadOnly = isReadOnly;
        this.presetInfo = null;
    }

    private RestPath(RestFileSystem fileSystem, List<String> path, boolean isReadOnly, boolean isAbsolute) {
        this.fileSystem = fileSystem;
        this.path = new LinkedList<>(path);
        this.isReadOnly = isReadOnly;
        this.isAbsolute = isAbsolute;
        this.presetInfo = null;
    }
    
    private RestPath(RestFileSystem fileSystem, List<String> path, boolean isReadOnly, boolean isAbsolute, RestFileInfo info) {
        this.fileSystem = fileSystem;
        this.path = new LinkedList<>(path);
        this.isReadOnly = isReadOnly;
        this.isAbsolute = isAbsolute;
        this.presetInfo = info;
        this.isVersionedFile = info.isVersionedFile();
    }

    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return isAbsolute;
    }

    @Override
    public Path getRoot() {
        return fileSystem.getRootDirectories().iterator().next();
    }

    @Override
    public Path getFileName() {
        return new RestPath(fileSystem, path.getLast(), isReadOnly);
    }

    @Override
    public Path getParent() {
        return path.isEmpty() ? null : new RestPath(fileSystem, path.subList(0, path.size() - 1), isReadOnly, isAbsolute);
    }

    @Override
    public int getNameCount() {
        return path.size();
    }

    @Override
    public Path getName(int index) {
        return new RestPath(fileSystem, path.get(index), isReadOnly);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Path normalize() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Path relativize(Path other) {
       if (other.getFileSystem() != this.fileSystem) {
           throw new IllegalArgumentException("Incompatible file system");
       }
       RestPath otherAbsolute = (RestPath) other.toAbsolutePath();
       RestPath thisAbsolute = (RestPath) this.toAbsolutePath();
       int commonRootDepth = 0;
       for (int i=0; i<Math.min(thisAbsolute.path.size(), otherAbsolute.path.size()); i++) {
           if (!thisAbsolute.path.get(i).equals(otherAbsolute.path.get(i))) {
              break;
           }
           commonRootDepth++;
       }
       List<String> relativePath = new LinkedList<>();
       for (int i=commonRootDepth; i<thisAbsolute.path.size(); i++) {
           relativePath.add("..");
       }
       relativePath.addAll(otherAbsolute.path.subList(commonRootDepth, otherAbsolute.path.size()));
       return new RestPath(fileSystem, relativePath, true, false);
    }

    @Override
    public URI toUri() {
        return fileSystem.getURI(path);
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute) {
            return this;
        } else {
            return new RestPath(fileSystem, path, isReadOnly, true);
        }
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int compareTo(Path other) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String toString() {
        return (isAbsolute ? "/" : "") + String.join("/", path);
    }

    InputStream newInputStream(OpenOption[] options) throws IOException {
        // TODO: Remove explicit use of HttpConnection
        if (isVersionedFile()) {
            VersionOpenOption vo = getOptions(options, VersionOpenOption.class);
            if (vo == null) {
                vo = VersionOpenOption.DEFAULT;
            }
            URI uri = fileSystem.getRestURI("rest/version/download/", path);
            uri = UriBuilder.fromUri(uri).queryParam("version", vo.value()).build();
            return uri.toURL().openStream();
        } else {
            URI uri = fileSystem.getRestURI("rest/download/", path);
            return uri.toURL().openStream();
        }
    }

    OutputStream newOutputStream(OpenOption[] options) throws IOException {

        // TODO: Deal with options
        VersionOpenOption voo = getOptions(options, VersionOpenOption.class);
        String restPath = isVersionedFile() || voo != null ? "rest/version/upload/" : "rest/upload/";
        WebTarget target = fileSystem.getRestTarget(restPath, path);
        BlockingQueue<Future<Response>> queue = new ArrayBlockingQueue<>(1);
        PipedOutputStream out = new PipedOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                try {
                    Response response = queue.take().get();
                    checkResponse(response);
                } catch (InterruptedException x) {
                    throw new InterruptedIOException("Interrupt during file close");
                } catch (ExecutionException x) {
                    throw new IOException("Error during file close", x.getCause());
                }
            }

        };
        PipedInputStream in = new PipedInputStream(out);
        Future<Response> futureResponse = target.request(MediaType.APPLICATION_JSON).async().post(Entity.entity(in, MediaType.APPLICATION_OCTET_STREAM));
        queue.add(futureResponse);
        return out;
    }

    void move(RestPath target, CopyOption[] options) throws IOException {
        URI uri = UriBuilder.fromUri(fileSystem.getRestURI("rest/move/", path)).queryParam("target", String.join("/", target.path)).build();
        Response response = fileSystem.getRestTarget(uri).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
    }

    private <T extends OpenOption> T getOptions(OpenOption[] options, Class<T> optionClass) {
        for (OpenOption option : options) {
            if (optionClass.isInstance(option)) {
                return optionClass.cast(option);
            }
        }
        return null;
    }

    BasicFileAttributes getAttributes() throws IOException {
        RestFileInfo info = getRestFileInfo();
        if (info.isVersionedFile()) {
            VersionInfo vinfo = getVersionedRestFileInfo();
            RestFileInfo latest = vinfo.getVersions().get(vinfo.getDefault() - 1);
            latest.setVersionedFile(true);
            return new RestFileAttributes(latest);
        } else {
            return new RestFileAttributes(info);
        }
    }

    VersionedFileAttributes getVersionedAttributes() throws IOException {
        if (!isVersionedFile()) {
            throw new IOException("Cannot read versioned attributes for non-versioned file");
        }
        Response response = fileSystem.getRestTarget("rest/version/info/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        VersionInfo info = response.readEntity(VersionInfo.class);
        return new RestVersionedFileAttributes(info);
    }

    private VersionInfo getVersionedRestFileInfo() throws IOException {
        Response response = fileSystem.getRestTarget("rest/version/info/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        VersionInfo info = response.readEntity(VersionInfo.class);
        return info;
    }

    private RestFileInfo getRestFileInfo() throws IOException {
        if (presetInfo != null) {
            return presetInfo;
        } else {
            Response response = fileSystem.getRestTarget("rest/info/", path).request(MediaType.APPLICATION_JSON).get();
            checkResponse(response);
            RestFileInfo info = response.readEntity(RestFileInfo.class);
            return info;
        }
    }

    private void checkResponse(Response response) throws IOException {
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            if (response.getStatus() == IOExceptionResponse.RESPONSE_CODE) {
                IOExceptionResponse ioError = response.readEntity(IOExceptionResponse.class);
                try {
                    Class<? extends IOException> exceptionClass = Class.forName(ioError.getExceptionClass()).asSubclass(IOException.class);
                    Constructor<? extends IOException> constructor = exceptionClass.getConstructor(String.class);
                    IOException io = constructor.newInstance(ioError.getMessage());
                    throw io;
                } catch (ReflectiveOperationException ex) {
                    throw new IOException("Remote Exception " + ioError.getExceptionClass() + " " + ioError.getMessage());
                }
            } else {
                throw new IOException("Response code " + response.getStatus() + " " + response.getStatusInfo());
            }
        }
    }

    BasicFileAttributeView getFileAttributeView() {
        return new BasicFileAttributeView() {
            @Override
            public String name() {
                return "basic";
            }

            @Override
            public BasicFileAttributes readAttributes() throws IOException {
                return getAttributes();
            }

            @Override
            public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
    }

    VersionedFileAttributeView getVersionedAttributeView() {
        return new VersionedFileAttributeView() {

            @Override
            public void setDefaultVersion(int version) throws IOException {
                Response response = fileSystem.getRestTarget("rest/version/set/", path).request(MediaType.APPLICATION_JSON).put(Entity.entity(version, MediaType.APPLICATION_JSON));
                checkResponse(response);
            }

            @Override
            public String name() {
                return "versioned";
            }

            @Override
            public VersionedFileAttributes readAttributes() throws IOException {
                return getVersionedAttributes();
            }

        };
    }

    void checkAccess(AccessMode... modes) throws IOException {
        Response response = fileSystem.getRestTarget("rest/list/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        response.readEntity(RestFileInfo.class);
    }

    // TODO: Implement filter
    DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter) throws IOException {
        Response response = fileSystem.getRestTarget("rest/list/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        RestFileInfo dirList = response.readEntity(RestFileInfo.class);
        final List<RestFileInfo> children = dirList.getChildren();
        if (children == null) {
            throw new NotDirectoryException(this.toString());
        }
        List<Path> paths = children.stream().map(fileInfo -> {
            List<String> newPath = new ArrayList<>(path);
            newPath.add(fileInfo.getName());
            return new RestPath(fileSystem, newPath, isReadOnly, true, fileInfo);
        }).collect(Collectors.toList());
        return new DirectoryStream<Path>() {
            @Override
            public Iterator<Path> iterator() {
                return paths.iterator();
            }

            @Override
            public void close() throws IOException {
            }
        };
    }

    void delete() throws IOException {
        String restPath = isVersionedFile() ? "rest/version/deleteFile/" : "rest/deleteFile/";
        Response response = fileSystem.getRestTarget(restPath, path).request(MediaType.APPLICATION_JSON).delete();
        checkResponse(response);
    }

    void createDirectory(FileAttribute<?>[] attrs) throws IOException {
        Response response = fileSystem.getRestTarget("rest/createDirectory/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
    }

    Map<String, Object> readAttributes(String attributes) throws IOException {
        final RestFileInfo restFileInfo = getRestFileInfo();
        final Map<String, Object> result = restFileInfo.toMap();
        if (restFileInfo.isVersionedFile()) {
            result.putAll(getVersionedRestFileInfo().toMap());
        }
        return result;
    }

    private synchronized boolean isVersionedFile() throws IOException {
        if (isVersionedFile == null) {
            try {
                RestFileInfo info = getRestFileInfo();
                isVersionedFile = info.isVersionedFile();
            } catch (NoSuchFileException | FileNotFoundException x) {
                // We do not know if it may be versioned in future
            }
        }
        return isVersionedFile != null && isVersionedFile;
    }

    @Override
    public boolean startsWith(Path other) {
        if (!other.getFileSystem().equals(this.getFileSystem())) {
            return false;
        }

        RestPath otherPath = (RestPath) other;
        if (otherPath.path.size() > this.path.size()) {
            return false;
        }
        for (int i = 0; i < otherPath.path.size(); i++) {
            if (!otherPath.path.get(i).equals(this.path.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean endsWith(Path other) {
        if (!other.getFileSystem().equals(this.getFileSystem())) {
            return false;
        }

        RestPath otherPath = (RestPath) other;
        if (otherPath.path.size() > this.path.size()) {
            return false;
        }
        for (int i = otherPath.path.size() - 1; i > 0; i--) {
            if (!otherPath.path.get(i).equals(this.path.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Path resolve(Path other) {
        if (other.isAbsolute()) {
            return other;
        }
        if (other.getNameCount() == 0) {
            return this;
        }
        List<String> newPath = new LinkedList<>(this.path);
        for (int i = 0; i < other.getNameCount(); i++) {
            newPath.add(other.getName(i).toString());
        }
        return new RestPath(this.fileSystem, newPath, this.isReadOnly, this.isAbsolute);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.fileSystem);
        hash = 89 * hash + Objects.hashCode(this.path);
        hash = 89 * hash + (this.isAbsolute ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RestPath other = (RestPath) obj;
        if (this.isAbsolute != other.isAbsolute) {
            return false;
        }
        if (!Objects.equals(this.fileSystem, other.fileSystem)) {
            return false;
        }
        return Objects.equals(this.path, other.path);
    }

    boolean isSameFile(RestPath other) {
        return this.toAbsolutePath().equals(other.toAbsolutePath());
    }

}
