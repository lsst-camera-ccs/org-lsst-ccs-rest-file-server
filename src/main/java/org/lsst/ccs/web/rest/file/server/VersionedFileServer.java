package org.lsst.ccs.web.rest.file.server;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 * Rest interface with functions specific to versioned files
 *
 * @author tonyj
 */
@Path("version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionedFileServer {

    java.nio.file.Path baseDir = Paths.get("/home/tonyj/Data");

    @GET
    @Path("info/{filePath: .*}")
    public Object info(@PathParam("filePath") String filePath) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile cf = new VersionedFile(path);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("default", cf.getDefaultVersion());
        result.put("latest", cf.getLatestVersion());
        List<Map<String, Object>> fileVersions = new ArrayList<>();
        int[] versions = cf.getVersions();      
        for (int version : versions) {
            Map<String, Object> fileVersion = new LinkedHashMap<>();
            fileVersion.put("version", version);
            java.nio.file.Path child = cf.getPathForVersion(version);
            fileVersion.put("lastModified", Files.getLastModifiedTime(child).toMillis());
            fileVersion.put("size", Files.size(child));
            fileVersions.add(fileVersion);
        }
        result.put("versions", fileVersions);
        return result;
    }

    @GET
    @Path("download/{filePath: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response file(@PathParam("filePath") String filePath, @QueryParam("version") String version) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        int versionNumber = computeVersion(vf, version);
        java.nio.file.Path fileToReturn = vf.getPathForVersion(versionNumber);
        StreamingOutput fileStream = (java.io.OutputStream output) -> {
            byte[] data = Files.readAllBytes(fileToReturn);
            output.write(data);
            output.flush();
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename = " + path.getFileName())
                .header("version", versionNumber)
                .build();
    }

    private int computeVersion(VersionedFile vf, String version) throws IOException, NumberFormatException {
        int versionNumber;
        if (version == null || version.isEmpty()) {
            version = "default";
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
    public Response diff(@PathParam("filePath") String filePath, @QueryParam("v1") String v1, @QueryParam("v2") String v2) throws IOException, DiffException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);

        int iv1 = computeVersion(vf, v1);
        int iv2 = computeVersion(vf, v2);

        java.nio.file.Path file1 = vf.getPathForVersion(iv1);
        java.nio.file.Path file2 = vf.getPathForVersion(iv2);
        List<String> lines1 = Files.readAllLines(file1);
        List<String> lines2 = Files.readAllLines(file2);
        Patch<String> diff = DiffUtils.diff(lines1, lines2);
        List<String> generateUnifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(vf.getFileName() + ";" + v1, vf.getFileName() + ";" + v2, lines1, diff, 2);

        StreamingOutput fileStream = (java.io.OutputStream output) -> {
            for (String dLines : generateUnifiedDiff) {
                output.write(dLines.getBytes());
                output.write('\n');
                output.flush();
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename = " + path.getFileName())
                .build();
    }

    @PUT
    @Path("set/{filePath: .*}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object set(@PathParam("filePath") String filePath, int defaultVersion) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        vf.setDefaultVersion(defaultVersion);
        return info(filePath);
    }

    @POST
    @Path("upload/{filePath: .*}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Object upload(@PathParam("filePath") String filePath, byte[] content) throws IOException {
        java.nio.file.Path path = baseDir.resolve(filePath);
        VersionedFile vf = new VersionedFile(path);
        int newVersion = vf.addVersion(content);
        return Collections.singletonMap("version", newVersion);
    }

}
