package org.lsst.ccs.rest.file.server.client.examples;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Properties;
import org.lsst.ccs.rest.file.server.client.VersionOption;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;

/**
 *
 * @author tonyj
 */
public class Main3 {

    public static void main(String[] args) throws IOException {
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        FileSystem restfs = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
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
