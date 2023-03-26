package org.lsst.ccs.rest.file.server.client.examples;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 *
 * @author tonyj
 */
public class SpeedTest {

    public static void main(String[] args) throws IOException {
        final Path tempDir = Files.createTempDirectory("rfs");
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        Map<String, Object> env = RestFileSystemOptions.builder()
                .cacheLocation(tempDir)
                .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                .set(RestFileSystemOptions.CacheFallback.OFFLINE)
                .set(RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE)
                .build();
        
        FileSystem restfs = FileSystems.newFileSystem(uri, env);

        Path pathInRestServer0 = restfs.getPath("dictionaries/data/DemoConfigurableSubsystem/1797891750.ser");
        readFile(pathInRestServer0);

        Path pathInRestServer = restfs.getPath("dictionaries/data/FocalPlane/3702060141.ser");
        readFile(pathInRestServer);
        // Second time should come from cache
        readFile(pathInRestServer);

        Path pathInRestServer2 = restfs.getPath("dictionaries/command/FocalPlane/846244239.ser");
        readFile(pathInRestServer2);
        // Second time should come from cache
        readFile(pathInRestServer2);
    }

    private static void readFile(Path pathInRestServer) throws IOException {
        byte[] buffer = new byte[32768];
        int length = 0;
        long start = System.currentTimeMillis();
        try (InputStream inputStream = Files.newInputStream(pathInRestServer)) {
            for (;;) {
                int l = inputStream.read(buffer, length, buffer.length - length);
                if (l < 0) {
                    break;
                }
                length += l;
            }
        }
        long stop = System.currentTimeMillis();
        System.out.printf("Read %d bytes in %dms\n", length, stop - start);
    }
}
