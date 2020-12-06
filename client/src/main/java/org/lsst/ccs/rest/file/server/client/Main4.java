package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author tonyj
 */
public class Main4 {

    public static void main(String[] args) throws IOException {
        Map<String, Object> env = new HashMap<>();
        env.put("useSSL", Boolean.TRUE);
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        FileSystem restfs = FileSystems.newFileSystem(uri, env);
        Path pathInRestServer = restfs.getPath("newTest.properties");
//        boolean exists = Files.exists(pathInRestServer);
//        if (exists) Files.delete(pathInRestServer);
        Properties props = new Properties();
        props.put("key", String.valueOf(Math.random()));
        props.put("key2", String.valueOf(Math.random()));        
        try (OutputStream out = Files.newOutputStream(pathInRestServer, VersionOpenOption.CREATE_OR_UPDATE)) {
            props.store(out, "This is a comment");
        }
    }
}
