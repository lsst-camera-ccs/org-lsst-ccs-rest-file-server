package org.lsst.ccs.web.rest.file.server;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.jvnet.hk2.annotations.Optional;
import static org.lsst.ccs.web.rest.file.server.data.Constants.PROTOCOL_VERSION_HEADER;
import org.lsst.ccs.web.rest.file.server.data.VersionOptions;
import org.lsst.ccs.web.rest.file.server.data.VersionInfoV2;
import org.lsst.ccs.web.rest.file.server.jwt.JWTTokenNeeded;

/**
 * Rest interface with functions specific to versioned files
 *
 * @author tonyj
 */
@Path("version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionedFileServer {

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
    @Path("info/{filePath: .*}")
    public Response info(@PathParam("filePath") String filePath, @Context Request request, @HeaderParam(PROTOCOL_VERSION_HEADER) Integer protocolVersion) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile cf = new VersionedFile(path);
        List<VersionInfoV2.Version> fileVersions = new ArrayList<>();
        int[] versions = cf.getVersions();
        for (int version : versions) {
            java.nio.file.Path child = cf.getPathForVersion(version);
            BasicFileAttributes fileAttributes = Files.getFileAttributeView(child, BasicFileAttributeView.class).readAttributes();
            VersionInfoV2.Version info = new VersionInfoV2.Version(child, fileAttributes, version, cf.isHidden(version), cf.getComment(version));
            fileVersions.add(info);
        }
        VersionInfoV2 result = new VersionInfoV2(cf.getDefaultVersion(), cf.getLatestVersion(), fileVersions);
        Serializable finalResult = result.downgrade(protocolVersion);
        EntityTag eTag = new EntityTag(ETagHelper.computeEtag(finalResult));
        Response.ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder != null) {
            return builder.tag(eTag).build();
        }
        return Response.ok(finalResult)
                .tag(eTag)
                .build();
    }

    @GET
    @Path("download/{filePath: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response file(@PathParam("filePath") String filePath, @QueryParam("version") String version, @Context Request request) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        int versionNumber = computeVersion(vf, version);
        java.nio.file.Path fileToReturn = vf.getPathForVersion(versionNumber);
        Date lastModified = new Date(Files.getLastModifiedTime(fileToReturn).toMillis());
        Response.ResponseBuilder builder = request.evaluatePreconditions(lastModified);
        if (builder != null) {
            return builder.lastModified(lastModified).build();
        }
        StreamingOutput fileStream = (java.io.OutputStream output) -> {
            byte[] data = Files.readAllBytes(fileToReturn);
            output.write(data);
            output.flush();
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename = " + path.getFileName())
                .header("version", versionNumber)
                .lastModified(lastModified)
                .build();
    }

    private int computeVersion(VersionedFile vf, String version) throws IOException, NumberFormatException {
        return computeVersion(vf, version, "default");
    }

    private int computeVersion(VersionedFile vf, String version, String defaultVersion) throws IOException, NumberFormatException {
        int versionNumber;
        if (version == null || version.isEmpty()) {
            version = defaultVersion;
        }
        switch (version) {
            case "default":
                versionNumber = vf.getDefaultVersion();
                break;
            case "latest":
                versionNumber = vf.getLatestVersion();
                break;
            default:
                versionNumber = Integer.parseInt(version);
                break;
        }
        return versionNumber;
    }

    @GET
    @Path("diff/{filePath: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response diff(@PathParam("filePath") String filePath, @QueryParam("v1") String v1, @QueryParam("v2") String v2, @Context Request request) throws IOException, DiffException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);

        int iv1 = computeVersion(vf, v1, "latest");
        int iv2 = computeVersion(vf, v2, String.valueOf(iv1 - 1));
        if (iv2 < 1) {
            throw new IOException("No previous version");
        }

        java.nio.file.Path file1 = vf.getPathForVersion(iv1);
        java.nio.file.Path file2 = vf.getPathForVersion(iv2);
        List<String> lines1 = Files.readAllLines(file1);
        List<String> lines2 = Files.readAllLines(file2);
        Patch<String> diff = DiffUtils.diff(lines2, lines1);
        List<String> diffList = UnifiedDiffUtils.generateUnifiedDiff(vf.getFileName() + ";" + iv2, vf.getFileName() + ";" + iv1, lines1, diff, 2);
        EntityTag eTag = new EntityTag(ETagHelper.computeEtag((Serializable) diffList));
        Response.ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder != null) {
            return builder.tag(eTag).build();
        }
        StreamingOutput fileStream = (java.io.OutputStream output) -> {
            for (String dLines : diffList) {
                output.write(dLines.getBytes());
                output.write('\n');
                output.flush();
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename = " + path.getFileName())
                .tag(eTag)
                .build();
    }

    @PUT
    @Path("set/{filePath: .*}")
    @JWTTokenNeeded
    @Consumes(MediaType.APPLICATION_JSON)
    public Object set(@PathParam("filePath") String filePath, int defaultVersion, @Context Request request, @HeaderParam(PROTOCOL_VERSION_HEADER) Integer protocolVersion) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        vf.setDefaultVersion(defaultVersion);
        return info(filePath, request, protocolVersion);
    }

    @PUT
    @Path("hide/{filePath: .*}")
    @JWTTokenNeeded
    @Consumes(MediaType.APPLICATION_JSON)
    public Object hide(@PathParam("filePath") String filePath, int version, @Context Request request, @HeaderParam(PROTOCOL_VERSION_HEADER) Integer protocolVersion) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        vf.setHidden(version, true);
        return info(filePath, request, protocolVersion);
    }

    @PUT
    @Path("unhide/{filePath: .*}")
    @JWTTokenNeeded
    @Consumes(MediaType.APPLICATION_JSON)
    public Object unhide(@PathParam("filePath") String filePath, int version, @Context Request request, @HeaderParam(PROTOCOL_VERSION_HEADER) Integer protocolVersion) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        vf.setHidden(version, false);
        return info(filePath, request, protocolVersion);
    }

    @PUT
    @Path("setOptions/{filePath: .*}")
    @JWTTokenNeeded
    @Consumes(MediaType.APPLICATION_JSON)
    public Object setOptions(@PathParam("filePath") String filePath, VersionOptions options, @Context Request request, @HeaderParam(PROTOCOL_VERSION_HEADER) Integer protocolVersion) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        int version = options.getVersion();
        if (options.getHidden() != null) {
            vf.setHidden(version, options.getHidden());
        }
        if (options.getComment() != null) {
            vf.setComment(version, options.getComment());
        }
        if (options.getMakeDefault() != null && options.getMakeDefault()) {
            vf.setDefaultVersion(version);
        }
        return info(filePath, request, protocolVersion);
    }

    @POST
    @Path("upload/{filePath: .*}")
    @JWTTokenNeeded
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Object upload(@PathParam("filePath") String filePath, byte[] content) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        if (VersionedFile.isVersionedFile(path)) {
            VersionedFile vf = new VersionedFile(path);
            int newVersion = vf.addVersion(content, true);
            return Collections.singletonMap("version", newVersion);
        } else {
            VersionedFile vf = VersionedFile.create(path, content);
            return Collections.singletonMap("version", vf.getLatestVersion());
        }
    }

    @DELETE
    @JWTTokenNeeded
    @Path("deleteFile/{filePath: .*}")
    public Response deleteFile(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        vf.delete();
        return Response.ok().build();
    }

}
