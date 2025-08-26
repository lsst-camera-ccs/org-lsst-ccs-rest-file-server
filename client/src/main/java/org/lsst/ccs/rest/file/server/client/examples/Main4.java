package org.lsst.ccs.rest.file.server.client.examples;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

/**
 * Example that uploads a new properties file to the REST file server.
 */
public class Main4 {

    /**
     * Runs the example.
     *
     * @param args ignored
     * @throws IOException if the upload fails
     */
    public static void main(String[] args) throws IOException {
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        FileSystem restfs = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
        Path pathInRestServer = restfs.getPath("newTest.properties");
//        boolean exists = Files.exists(pathInRestServer);
//        if (exists) Files.delete(pathInRestServer);
        Properties props = new Properties();
        props.put("key", String.valueOf(Math.random()));
        props.put("key2", String.valueOf(Math.random()));        
        try (OutputStream out = Files.newOutputStream(pathInRestServer)) {
            props.store(out, "This is a comment");
        }
    }
}
