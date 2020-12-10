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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.jvnet.hk2.annotations.Optional;

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
    public Object list() throws IOException {
        return list("");
    }

    @GET
    @Path("list/{filePath: .*}")
    public Object list(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Map<String, Object> fileProperties = info(filePath);
        boolean isDirectory = Files.isDirectory(file);
        if (isDirectory) {
            Stream<java.nio.file.Path> listFiles = Files.list(file);
            List<Map<String, Object>> result = new ArrayList<>();
            listFiles.forEach((child) -> {
                try {
                    Map<String, Object> childProperties = new LinkedHashMap<>();
                    childProperties.put("name", child.getFileName().toString());
                    childProperties.put("size", Files.size(child));
                    childProperties.put("lastModified", Files.getLastModifiedTime(child).toMillis());
                    childProperties.put("isVersionedFile", VersionedFile.isVersionedFile(child));
                    result.add(childProperties);
                } catch (IOException x) {
                    System.out.println(x.getMessage());
                }
            });
            result.sort((Map<String, Object> o1, Map<String, Object> o2) -> o1.get("name").toString().compareTo(o2.get("name").toString()));
            fileProperties.put("children", result);
        }
        return fileProperties;
    }

    @GET
    @Path("info/{filePath: .*}")
    public Map<String, Object> info(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        BasicFileAttributes fileAttributes = Files.getFileAttributeView(file, BasicFileAttributeView.class).readAttributes();
        if (fileAttributes == null) {
            throw new NoSuchFileException(filePath);
        }
        Map<String, Object> fileProperties = new LinkedHashMap<>();
        fileProperties.put("name", file.getFileName().toString());
        fileProperties.put("size", fileAttributes.size());
        fileProperties.put("lastModified", fileAttributes.lastModifiedTime().toMillis());
        fileProperties.put("fileKey", fileAttributes.fileKey().toString());
        fileProperties.put("isDirectory", fileAttributes.isDirectory());
        fileProperties.put("isOther", fileAttributes.isOther());
        fileProperties.put("isRegularFile", fileAttributes.isRegularFile());
        fileProperties.put("isSymbolicLink", fileAttributes.isSymbolicLink());
        fileProperties.put("lastAccessTime", fileAttributes.lastAccessTime().toMillis());
        fileProperties.put("creationTime", fileAttributes.creationTime().toMillis());
        fileProperties.put("mimeType", Files.probeContentType(file));
        fileProperties.put("isVersionedFile", VersionedFile.isVersionedFile(file));
        return fileProperties;
    }

    @GET
    @Path("download/{filePath: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response file(@PathParam("filePath") String filePath) {
        java.nio.file.Path file = baseDir.resolve(filePath);
        if (file.toFile().canRead()) {
            StreamingOutput fileStream = (java.io.OutputStream output) -> {
                byte[] data = Files.readAllBytes(file);
                output.write(data);
                output.flush();
            };
            return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                    .header("content-disposition", "attachment; filename = " + file.getFileName())
                    .build();
        } else {
            return Response.status(404, "File not readable: " + file).build();
        }
    }

    @GET
    @Path("createDirectory/{filePath: .*}")
    public Response createDirectory(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.createDirectory(file);
        return Response.ok().build();
    }

    @GET
    @Path("createFile/{filePath: .*}")
    public Response createFile(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path file = baseDir.resolve(filePath);
        Files.createFile(file);
        return Response.ok().build();
    }

    @GET
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
