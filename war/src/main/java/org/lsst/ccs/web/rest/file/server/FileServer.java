package org.lsst.ccs.web.rest.file.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;
import org.jvnet.hk2.annotations.Optional;
import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;

/**
 * JAX-RS resource providing operations on non-versioned files. It exposes
 * endpoints to list directory contents, retrieve file metadata, download and
 * upload files, and perform basic file manipulations such as move or delete.
 *
 * @author tonyj
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class FileServer {

    @Inject
    @Optional
    private java.nio.file.Path baseDir;

    /**
     * Initializes the base directory from the servlet context if provided.
     *
     * @param context the servlet context containing configuration
     * @throws IOException if the configured directory cannot be resolved
     */
    @Context
    public void setServletContext(ServletContext context) throws IOException {
        if (context != null) {
            String initParameter = context.getInitParameter("org.lsst.ccs.web.rest.file.server.baseDir");
            if (initParameter != null) {
                baseDir = Paths.get(initParameter);
            }
        }
        if (baseDir == null) {
            baseDir = Paths.get("/home/tonyj/ConfigTest/");
        }
    }

    /**
     * Lists the contents of the server's base directory.
     *
     * @param request the HTTP precondition request
     * @return a response containing information about the directory contents
     * @throws IOException if the listing fails
     */
    @GET
    @Path("list")
    public Response list(@Context Request request) throws IOException {
        return list("", request);
    }

    /**
     * Lists the contents of the specified directory or returns information
     * about a file if the path points to a file.
     *
     * @param filePath relative path of the file or directory
     * @param request the HTTP precondition request
     * @return metadata for the requested file or directory
     * @throws IOException if the path cannot be read
     */
    @GET
    @Path("list/{filePath: .*}")
    public Response list(@PathParam("filePath") String filePath, @Context Request request) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        RestFileInfo fileProperties;
        boolean isDirectory = Files.isDirectory(file);
        if (isDirectory) {
            List<java.nio.file.Path> listFiles;
            try (Stream<java.nio.file.Path> list = Files.list(file)) {
                listFiles = list.collect(Collectors.toList());
            }
            List<RestFileInfo> children = new ArrayList<>();
            for (java.nio.file.Path child : listFiles) {
                BasicFileAttributes childAttributes = Files.getFileAttributeView(child, BasicFileAttributeView.class).readAttributes();
                final boolean isVersioned = VersionedFile.isVersionedFile(child);
                if (isVersioned) {
                   VersionedFile vf = new VersionedFile(child);
                   childAttributes = Files.getFileAttributeView(vf.getLatest(), BasicFileAttributeView.class).readAttributes();
                }
                RestFileInfo childProperties = new RestFileInfo(child, childAttributes, isVersioned);
                children.add(childProperties);
            }
            children.sort((RestFileInfo o1, RestFileInfo o2) -> o1.getName().compareTo(o2.getName()));
            fileProperties = getFileAtrributes(file, filePath, children);
        } else {
            fileProperties = getFileAtrributes(file, filePath, null);
        }

        EntityTag eTag = new EntityTag(ETagHelper.computeEtag(fileProperties));
        ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder != null) {
            return builder.tag(eTag).build();
        }
        return Response.ok(fileProperties)
                .tag(eTag)
                .build();
    }

    /**
     * Retrieves metadata for the specified file or directory.
     *
     * @param filePath relative path of the file or directory
     * @param request the HTTP precondition request
     * @return a response containing the file information
     * @throws IOException if the file attributes cannot be read
     */
    @GET
    @Path("info/{filePath: .*}")
    public Response info(@PathParam("filePath") String filePath, @Context Request request) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        final RestFileInfo fileAtrributes = getFileAtrributes(file, filePath, null);
        EntityTag eTag = new EntityTag(ETagHelper.computeEtag(fileAtrributes));
        ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder != null) {
            return builder.tag(eTag).build();
        }
        return Response.ok(fileAtrributes)
                .tag(eTag)
                .build();
    }

    private RestFileInfo getFileAtrributes(java.nio.file.Path file, String filePath, List<RestFileInfo> children) throws IOException, NoSuchFileException {
        BasicFileAttributes fileAttributes = Files.getFileAttributeView(file, BasicFileAttributeView.class).readAttributes();
        if (fileAttributes == null) {
            throw new NoSuchFileException(filePath);
        }
        return new RestFileInfo(file, fileAttributes, VersionedFile.isVersionedFile(file), children);
    }

    /**
     * Streams the specified file to the client.
     *
     * @param filePath relative path of the file to download
     * @param request the HTTP precondition request
     * @return the file content as an octet-stream
     * @throws IOException if the file cannot be read
     */
    @GET
    @Path("download/{filePath: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response file(@PathParam("filePath") String filePath, @Context Request request) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        if (Files.isReadable(file)) {
            Date lastModified = new Date(Files.getLastModifiedTime(file).toMillis());
            ResponseBuilder builder = request.evaluatePreconditions(lastModified);
            if (builder != null) {
                return builder.lastModified(lastModified).build();
            }
            StreamingOutput fileStream = (java.io.OutputStream output) -> {
                byte[] data = Files.readAllBytes(file);
                output.write(data);
                output.flush();
            };
            return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                    .lastModified(lastModified)
                    .header("content-disposition", "attachment; filename = " + file.getFileName())
                    .build();
        } else {
            return Response.status(404, "File not readable: " + file).build();
        }
    }

    /**
     * Creates a new directory at the specified path.
     *
     * @param filePath relative path of the directory to create
     * @return an empty success response
     * @throws IOException if creation fails
     */
    @POST
    @Path("createDirectory/{filePath: .*}")
    public Response createDirectory(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.createDirectory(file);
        return Response.ok().build();
    }

    /**
     * Creates an empty file at the specified path.
     *
     * @param filePath relative path of the file to create
     * @return an empty success response
     * @throws IOException if creation fails
     */
    @POST
    @Path("createFile/{filePath: .*}")
    public Response createFile(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.createFile(file);
        return Response.ok().build();
    }

    /**
     * Moves or renames a file or directory.
     *
     * @param source source path relative to the base directory
     * @param target destination path relative to the base directory
     * @return an empty success response
     * @throws IOException if the move fails
     */
    @POST
    @Path("move/{filePath: .*}")
    public Response move(@PathParam("filePath") String source, @QueryParam("target") String target) throws IOException {
        java.nio.file.Path sourcePath = baseDir.resolve(source);
        java.nio.file.Path targetPath = baseDir.resolve(target);
        Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        return Response.ok().build();
    }

    /**
     * Deletes the specified file or directory.
     *
     * @param filePath path of the file or directory to delete
     * @return an empty success response
     * @throws IOException if deletion fails
     */
    @DELETE
    @Path("deleteFile/{filePath: .*}")
    public Response deleteFile(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.delete(file);
        return Response.ok().build();
    }

    /**
     * Uploads content to the specified file using the given open options.
     *
     * @param filePath relative path of the target file
     * @param openOptions file open options such as {@code CREATE_NEW}
     * @param content the bytes to write
     * @return an empty success response
     * @throws IOException if writing fails
     */
    @POST
    @Path("upload/{filePath: .*}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response upload(@PathParam("filePath") String filePath, @QueryParam("openOption") List<String> openOptions, byte[] content) throws IOException {
        StandardOpenOption[] soo;
        if (openOptions == null || openOptions.isEmpty()) {
            soo = new StandardOpenOption[]{ StandardOpenOption.CREATE_NEW };
        } else {
            soo = openOptions.stream().map(s -> StandardOpenOption.valueOf(s)).toArray(StandardOpenOption[]::new);
        }
        java.nio.file.Path path = baseDir.resolve(filePath);
        try (OutputStream out = Files.newOutputStream(path, soo)) {
            out.write(content);
        }
        return Response.ok().build();
    }
}
