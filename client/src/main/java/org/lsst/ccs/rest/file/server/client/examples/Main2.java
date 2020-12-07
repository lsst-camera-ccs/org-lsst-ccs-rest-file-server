package org.lsst.ccs.rest.file.server.client.examples;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;

/**
 *
 * @author tonyj
 */
public class Main2 {

    public static void main(String[] args) throws IOException {
        Path path = Paths.get("/home/tonyj/Untitled9.ipynb");
        Map<String, Object> readAttributes = Files.readAttributes(path, "*");
        System.out.println(readAttributes);

        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        FileSystem restfs = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
        Path pathInRestServer = restfs.getPath("test.properties");
        Map<String, Object> readAttributes2 = Files.readAttributes(pathInRestServer, "*");
        System.out.println(readAttributes2);
//        String restUri = "http://localhost:8080/rest-file-server/rest/list/testVersions/test.file/4";
//        Client client = ClientBuilder.newClient();
//        RestFileInfo r = client.target(restUri).request(MediaType.APPLICATION_JSON).get(RestFileInfo.class);
//        System.out.println(r);
//        System.out.println(r.getMetadata());
//        System.out.println(r.getEntity());

        VersionedFileAttributeView fileAttributeView = Files.getFileAttributeView(pathInRestServer, VersionedFileAttributeView.class);
        System.out.println(fileAttributeView);

    }

}
