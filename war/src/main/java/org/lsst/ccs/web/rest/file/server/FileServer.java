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
 * Rest interface which works with any files
 *
 * @author tonyj
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class FileServer {

    @Inject
    @Optional
    private java.nio.file.Path baseDir;

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

    @GET
    @Path("list")
    public Response list(@Context Request request) throws IOException {
        return list("", request);
    }

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

    @POST
    @Path("createDirectory/{filePath: .*}")
    public Response createDirectory(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.createDirectory(file);
        return Response.ok().build();
    }

    @POST
    @Path("createFile/{filePath: .*}")
    public Response createFile(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.createFile(file);
        return Response.ok().build();
    }

    @POST
    @Path("move/{filePath: .*}")
    public Response move(@PathParam("filePath") String source, @QueryParam("target") String target) throws IOException {
        java.nio.file.Path sourcePath = baseDir.resolve(source);
        java.nio.file.Path targetPath = baseDir.resolve(target);
        Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        return Response.ok().build();
    }

    @DELETE
    @Path("deleteFile/{filePath: .*}")
    public Response deleteFile(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.delete(file);
        return Response.ok().build();
    }

    @POST
    @Path("upload/{filePath: .*}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response upload(@PathParam("filePath") String filePath, byte[] content) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            out.write(content);
        }
        return Response.ok().build();
    }
}
