package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author tonyj
 */
public class Main3 {

    public static void main(String[] args) throws IOException {
        Map<String, Object> env = new HashMap<>();
        env.put("useSSL", Boolean.TRUE);
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        FileSystem restfs = FileSystems.newFileSystem(uri, env);
        Path pathInRestServer = restfs.getPath("test.properties");
        VersionedFileAttributeView attrs = Files.getFileAttributeView(pathInRestServer, VersionedFileAttributeView.class);
        int latest = attrs.readAttributes().getLatestVersion();
        Properties props = new Properties();
        props.put("$$VERSION", String.valueOf(latest));

        try (InputStream inputStream = Files.newInputStream(pathInRestServer, StandardOpenOption.READ, VersionOption.of(latest))) {
            props.load(inputStream);
        }
        props.list(System.out);
    }
}
