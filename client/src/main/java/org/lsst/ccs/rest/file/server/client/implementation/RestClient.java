package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.Closeable;
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
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.web.rest.file.server.data.IOExceptionResponse;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;
import org.lsst.ccs.web.rest.file.server.data.VersionInfo;

/**
 *
 * @author tonyj
 */
public class RestClient implements Closeable {
    
    private final Client client;
    private final URI restURI;

    RestClient(Client client, URI restURI) {
        this.client = client;
        this.restURI = restURI; 
    }
    
    private URI getRestURI(String restPath, RestPath path) throws IOException {
        return restURI.resolve(restPath).resolve(path.getRestPath());
    }
    
    private WebTarget getRestTarget(String restPath, RestPath path) throws IOException {
        return client.target(getRestURI(restPath, path));
    }

    InputStream newInputStream(RestPath path, OpenOption[] options) throws IOException {
        // TODO: Remove explicit use of HttpConnection
        if (path.isVersionedFile()) {
            VersionOpenOption vo = getOptions(options, VersionOpenOption.class);
            if (vo == null) {
                vo = VersionOpenOption.DEFAULT;
            }
            URI uri = getRestURI("rest/version/download/", path);
            uri = UriBuilder.fromUri(uri).queryParam("version", vo.value()).build();
            return uri.toURL().openStream();
        } else {
            URI uri = getRestURI("rest/download/", path);
            return uri.toURL().openStream();
        }
    }

    OutputStream newOutputStream(RestPath path, OpenOption[] options) throws IOException {

        // TODO: Deal with options
        VersionOpenOption voo = getOptions(options, VersionOpenOption.class);
        String restPath = path.isVersionedFile() || voo != null ? "rest/version/upload/" : "rest/upload/";
        WebTarget target = getRestTarget(restPath, path);
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

    // TODO: Implement filter
    DirectoryStream<Path> newDirectoryStream(RestPath path, DirectoryStream.Filter<? super Path> filter) throws IOException {
        Response response = getRestTarget("rest/list/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        RestFileInfo dirList = response.readEntity(RestFileInfo.class);
        final List<RestFileInfo> children = dirList.getChildren();
        if (children == null) {
            throw new NotDirectoryException(this.toString());
        }
        List<Path> paths = children.stream().map(fileInfo -> path.resolve(fileInfo.getName())).collect(Collectors.toList());
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

    void createDirectory(RestPath path, FileAttribute<?>[] attrs) throws IOException {
        Response response = getRestTarget("rest/createDirectory/", path).request(MediaType.APPLICATION_JSON).post(null);
        checkResponse(response);
    }

    void delete(RestPath path) throws IOException {
        String restPath = path.isVersionedFile() ? "rest/version/deleteFile/" : "rest/deleteFile/";
        Response response = getRestTarget(restPath, path).request(MediaType.APPLICATION_JSON).delete();
        checkResponse(response);
    }

    void move(RestPath source, RestPath target, CopyOption[] options) throws IOException {
        URI uri = UriBuilder.fromUri(getRestURI("rest/move/", source)).queryParam("target", target.getRestPath()).build();
        Response response = client.target(uri).request(MediaType.APPLICATION_JSON).post(null);
        checkResponse(response);
    }

    void checkAccess(RestPath path, AccessMode... modes) throws IOException {
        Response response = getRestTarget("rest/list/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        response.readEntity(RestFileInfo.class);
    }

    BasicFileAttributes getAttributes(RestPath path, LinkOption[] options) throws IOException {
        RestFileInfo info = getRestFileInfo(path);
        if (info.isVersionedFile()) {
            VersionInfo vinfo = getVersionedRestFileInfo(path);
            RestFileInfo latest = vinfo.getVersions().get(vinfo.getDefault() - 1);
            latest.setVersionedFile(true);
            return new RestFileAttributes(latest);
        } else {
            return new RestFileAttributes(info);
        }
    }

    VersionedFileAttributes getVersionedAttributes(RestPath path, LinkOption[] options) throws IOException {
        if (!path.isVersionedFile()) {
            throw new IOException("Cannot read versioned attributes for non-versioned file");
        }
        Response response = getRestTarget("rest/version/info/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        VersionInfo info = response.readEntity(VersionInfo.class);
        return new RestVersionedFileAttributes(info);
    }

    BasicFileAttributeView getFileAttributeView(RestPath path, LinkOption[] options) {
        return new BasicFileAttributeView() {
            @Override
            public String name() {
                return "basic";
            }

            @Override
            public BasicFileAttributes readAttributes() throws IOException {
                return getAttributes(path, options);
            }

            @Override
            public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
    }

    VersionedFileAttributeView getVersionedAttributeView(RestPath path, LinkOption[] options) {
        return new VersionedFileAttributeView() {

            @Override
            public void setDefaultVersion(int version) throws IOException {
                Response response = getRestTarget("rest/version/set/", path).request(MediaType.APPLICATION_JSON).put(Entity.entity(version, MediaType.APPLICATION_JSON));
                checkResponse(response);
            }

            @Override
            public String name() {
                return "versioned";
            }

            @Override
            public VersionedFileAttributes readAttributes() throws IOException {
                return getVersionedAttributes(path, options);
            }

        };
    }

    Map<String, Object> readAttributes(RestPath path, String attributes, LinkOption[] options) throws IOException {
        final RestFileInfo restFileInfo = getRestFileInfo(path);
        final Map<String, Object> result = restFileInfo.toMap();
        if (restFileInfo.isVersionedFile()) {
            result.putAll(getVersionedRestFileInfo(path).toMap());
        }
        return result;
    }

    private VersionInfo getVersionedRestFileInfo(RestPath path) throws IOException {
        Response response = getRestTarget("rest/version/info/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        VersionInfo info = response.readEntity(VersionInfo.class);
        return info;
    }

    RestFileInfo getRestFileInfo(RestPath path) throws IOException {
        Response response = getRestTarget("rest/info/", path).request(MediaType.APPLICATION_JSON).get();
        checkResponse(response);
        RestFileInfo info = response.readEntity(RestFileInfo.class);
        return info;
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

    private <T extends OpenOption> T getOptions(OpenOption[] options, Class<T> optionClass) {
        for (OpenOption option : options) {
            if (optionClass.isInstance(option)) {
                return optionClass.cast(option);
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

}
