package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.Closeable;
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
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.rest.file.server.client.VersionedOpenOption;
import org.lsst.ccs.web.rest.file.server.data.IOExceptionResponse;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;
import org.lsst.ccs.web.rest.file.server.data.VersionInfo;

/**
 *
 * @author tonyj
 */
class RestClient implements Closeable {

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
        URI uri;
        if (path.isVersionedFile()) {
            if (hasOption(options, VersionedOpenOption.DIFF)) {
                UriBuilder builder = UriBuilder.fromUri(getRestURI("rest/version/diff/", path));
                List<VersionOpenOption> vos = getOptions(options, VersionOpenOption.class);
                if (vos.size() > 0) { 
                    builder.queryParam("v1", vos.get(0).value());
                    if (vos.size() > 1) { 
                        builder.queryParam("v2", vos.get(1).value());
                    }
                }
                uri = builder.build();
            } else {
                VersionOpenOption vo = getOption(options, VersionOpenOption.class);
                if (vo == null) {
                    vo = VersionOpenOption.DEFAULT;
                }
                uri = UriBuilder.fromUri(getRestURI("rest/version/download/", path)).queryParam("version", vo.value()).build();
            }
        } else {
            uri = getRestURI("rest/download/", path);
        }
        WebTarget target = client.target(uri);
        Response response = target.request(MediaType.APPLICATION_OCTET_STREAM).get();
        if (response.getStatus() == 404) {
            throw new FileNotFoundException(path.toString());
        }
        checkResponse(response);
        return response.readEntity(InputStream.class);
    }

    OutputStream newOutputStream(RestPath path, OpenOption[] options) throws IOException {

        // TODO: Deal with options
        VersionOpenOption voo = getOption(options, VersionOpenOption.class);
        boolean isVersionedFile;
        try {
            isVersionedFile = voo != null || path.isVersionedFile();
        } catch (IOException x) {
            isVersionedFile = false;
        }
        String restPath = isVersionedFile ? "rest/version/upload/" : "rest/upload/";
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
                    final Throwable cause = x.getCause();
                    if (cause instanceof ProcessingException) {
                        throw convertProcessingException((ProcessingException) cause);
                    } else if (cause instanceof IOException) {
                        throw (IOException) cause;
                    } else {
                        throw new IOException("Error during file close", cause);
                    }
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
        Response response = getAndCheckResponse(getRestTarget("rest/list/", path).request(MediaType.APPLICATION_JSON));
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
        Response response = postAndCheckResponse(getRestTarget("rest/createDirectory/", path).request(MediaType.APPLICATION_JSON), null);
    }

    void delete(RestPath path) throws IOException {
        String restPath = path.isVersionedFile() ? "rest/version/deleteFile/" : "rest/deleteFile/";
        Response response = deleteAndCheckResponse(getRestTarget(restPath, path).request(MediaType.APPLICATION_JSON));
    }

    void move(RestPath source, RestPath target,
            CopyOption[] options) throws IOException {
        URI uri = UriBuilder.fromUri(getRestURI("rest/move/", source)).queryParam("target", target.getRestPath()).build();
        Response response = postAndCheckResponse(client.target(uri).request(MediaType.APPLICATION_JSON), null);
    }

    void checkAccess(RestPath path, AccessMode... modes) throws IOException {
        Response response = getAndCheckResponse(getRestTarget("rest/list/", path).request(MediaType.APPLICATION_JSON));
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
        Response response = getAndCheckResponse(getRestTarget("rest/version/info/", path).request(MediaType.APPLICATION_JSON));
        VersionInfo info = response.readEntity(VersionInfo.class);
        return new RestVersionedFileAttributes(info);
    }

    BasicFileAttributeView getFileAttributeView(RestPath path, LinkOption[] options
    ) {
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

    VersionedFileAttributeView getVersionedAttributeView(RestPath path, LinkOption[] options
    ) {
        return new VersionedFileAttributeView() {

            @Override
            public void setDefaultVersion(int version) throws IOException {
                Response response = putAndCheckResponse(getRestTarget("rest/version/set/", path).request(MediaType.APPLICATION_JSON), Entity.entity(version, MediaType.APPLICATION_JSON));
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

    Map<String, Object> readAttributes(RestPath path, String attributes,
            LinkOption[] options) throws IOException {
        final RestFileInfo restFileInfo = getRestFileInfo(path);
        final Map<String, Object> result = restFileInfo.toMap();
        if (restFileInfo.isVersionedFile()) {
            result.putAll(getVersionedRestFileInfo(path).toMap());
        }
        return result;
    }

    private VersionInfo getVersionedRestFileInfo(RestPath path) throws IOException {
        Response response = getAndCheckResponse(getRestTarget("rest/version/info/", path).request(MediaType.APPLICATION_JSON));
        VersionInfo info = response.readEntity(VersionInfo.class);
        return info;
    }

    RestFileInfo getRestFileInfo(RestPath path) throws IOException {
        Response response = getAndCheckResponse(getRestTarget("rest/info/", path).request(MediaType.APPLICATION_JSON));
        RestFileInfo info = response.readEntity(RestFileInfo.class);
        return info;
    }

    private Response getAndCheckResponse(SyncInvoker invoker) throws IOException {
        try {
            Response response = invoker.get();
            checkResponse(response);
            return response;
        } catch (ProcessingException x) {
            throw convertProcessingException(x);
        }
    }

    private Response putAndCheckResponse(Invocation.Builder request, Entity<?> entity) throws IOException {
        try {
            Response response = request.put(entity);
            checkResponse(response);
            return response;
        } catch (ProcessingException x) {
            throw convertProcessingException(x);
        }
    }

    private Response postAndCheckResponse(Invocation.Builder request, Entity<?> entity) throws IOException {
        try {
            Response response = request.post(entity);
            checkResponse(response);
            return response;
        } catch (ProcessingException x) {
            throw convertProcessingException(x);
        }
    }

    private Response deleteAndCheckResponse(Invocation.Builder request) throws IOException {
        try {
            Response response = request.delete();
            checkResponse(response);
            return response;
        } catch (ProcessingException x) {
            throw convertProcessingException(x);
        }
    }

    static IOException convertProcessingException(ProcessingException x) {
        if (x.getCause() instanceof IOException) {
            return (IOException) x.getCause();
        } else {
            return new IOException("Error talking to rest server", x);
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

    private <T extends OpenOption> T getOption(OpenOption[] options, Class<T> optionClass) {
        for (OpenOption option : options) {
            if (optionClass.isInstance(option)) {
                return optionClass.cast(option);
            }
        }
        return null;
    }

    private <T extends OpenOption> List<T> getOptions(OpenOption[] options, Class<T> optionClass) {
        List<T> result = new ArrayList<>();
        for (OpenOption option : options) {
            if (optionClass.isInstance(option)) {
                result.add(optionClass.cast(option));
            }
        }
        return result;
    }

    private boolean hasOption(OpenOption[] options, Enum<?> search) {
        for (OpenOption option : options) {
            if (search.equals(option)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

}
