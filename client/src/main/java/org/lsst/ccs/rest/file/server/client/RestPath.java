package org.lsst.ccs.rest.file.server.client;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
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
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.web.rest.file.server.data.VersionInfo;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;

/**
 *
 * @author tonyj
 */
public class RestPath extends AbstractPath {

    private final RestFileSystem fileSystem;
    private final LinkedList<String> path;
    private final boolean isReadOnly;
    private final boolean isAbsolute;

    RestPath(RestFileSystem fileSystem, String path, boolean isReadOnly) {
        this.fileSystem = fileSystem;
        this.path = new LinkedList<>(Arrays.asList(path.split("/")));
        this.isReadOnly = isReadOnly;
        this.isAbsolute = path.startsWith("/");
    }

    RestPath(RestFileSystem fileSystem, List<String> path, boolean isReadOnly, boolean isAbsolute) {
        this.fileSystem = fileSystem;
        this.path = new LinkedList<>(path);
        this.isReadOnly = isReadOnly;
        this.isAbsolute = isAbsolute;
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public URI toUri() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute) return this;
        else {
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
        if (isVersionedFile()) {
            VersionOption vo = getOptions(options, VersionOption.class);
            if (vo == null) {
                vo = VersionOption.DEFAULT;
            }
            URI uri = fileSystem.getURI("rest/version/download/", path, "version=" + vo.value());
            return uri.toURL().openStream();
        } else {
            URI uri = fileSystem.getURI("rest/download/", path);
            return uri.toURL().openStream();
        }
    }

    OutputStream newOutputStream(OpenOption[] options) throws IOException {

        VersionOpenOption voo = getOptions(options, VersionOpenOption.class);
        String restPath = voo == null ? "rest/upload/" : "rest/version/upload/";
        URI uri = fileSystem.getURI(restPath, path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestMethod("POST");
        return new FilterOutputStream(connection.getOutputStream()) {
            @Override
            public void close() throws IOException {
                super.close();
                int rc = connection.getResponseCode();
                if (rc != 200) {
                    throw new IOException("Failed during close, rc=" + rc);
                }
            }
        };
    }

    void move(RestPath target, CopyOption[] options) throws IOException {
        Client client = ClientBuilder.newClient();
        URI uri = UriBuilder.fromUri(fileSystem.getURI("rest/move/", path)).queryParam("target", String.join("/", target.path)).build();
        Response response = client.target(uri).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException("Error moving file " + response.getStatus()+" for "+uri+" "+response.getStatusInfo());
        }
    }

    <T extends OpenOption> T getOptions(OpenOption[] options, Class<T> optionClass) {
        for (OpenOption option : options) {
            if (optionClass.isInstance(option)) {
                return optionClass.cast(option);
            }
        }
        return null;
    }

    BasicFileAttributes getAttributes() throws IOException {
        if (isVersionedFile()) {
            RestFileInfo info = getVersionedRestFileInfo();
            return new RestFileAttributes(info);            
        } else {
            RestFileInfo info = getRestFileInfo();
            return new RestFileAttributes(info);
        }
    }

    VersionedFileAttributes getVersionedAttributes() throws IOException {
        if (!isVersionedFile()) {
            throw new IOException("Cannot read versioned attributes for non-versioned file");
        }
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/version/info/", path)).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException("Bad response " + response.getStatus() + " from " + fileSystem.getURI("rest/version/info/", path));
        }
        VersionInfo info = response.readEntity(VersionInfo.class);
        return new RestVersionedFileAttributes(info);
    }

    private RestFileInfo getVersionedRestFileInfo() throws IOException {
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/version/info/", path)).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException("Bad response " + response.getStatus() + " from " + fileSystem.getURI("rest/info/", path));
        }
        RestFileInfo info = response.readEntity(RestFileInfo.class);
        return info;
    }
    
    private RestFileInfo getRestFileInfo() throws IOException {
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/info/", path)).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException("Bad response " + response.getStatus() + " from " + fileSystem.getURI("rest/info/", path));
        }
        RestFileInfo info = response.readEntity(RestFileInfo.class);
        return info;
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
            public void setDefaultVersion(int version) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/list/", path)).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new NoSuchFileException("No such file " + response.getStatus());
        } else {
            response.readEntity(RestFileInfo.class);
        }
    }

    DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter) throws IOException {
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/list/", path)).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new NoSuchFileException("No such file " + response.getStatus());
        } else {
            RestFileInfo dirList = response.readEntity(RestFileInfo.class);
            List<Path> paths = dirList.getChildren().stream().map(fileInfo -> {
                List<String> newPath = new ArrayList<>(path);
                newPath.add(fileInfo.getName());
                return new RestPath(fileSystem, newPath, isReadOnly, true);
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
    }

    void delete() throws IOException {
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/deleteFile/", path)).request(MediaType.APPLICATION_JSON).delete();
        if (response.getStatus() != 200) {
            throw new IOException("Error deleting file " + response.getStatus());
        }
    }

    void createDirectory(FileAttribute<?>[] attrs) throws IOException {
        Client client = ClientBuilder.newClient();
        Response response = client.target(fileSystem.getURI("rest/createDirectory/", path)).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException("Error creating directory " + response.getStatus());
        }
    }

    Map<String, Object> readAttributes(String attributes) throws IOException {
        return getRestFileInfo().getAsMap();
    }

    private boolean isVersionedFile() throws IOException {
        RestFileInfo info = getRestFileInfo();
        // TODO: Cache this?
        return info.isVersionedFile();
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
