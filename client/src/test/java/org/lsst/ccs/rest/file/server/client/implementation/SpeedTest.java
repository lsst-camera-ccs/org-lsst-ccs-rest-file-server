package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 *
 * @author tonyj
 */
public class SpeedTest {

    @Test
    public void testSpeed() throws IOException {
        final Path tempDir = Files.createTempDirectory("rfs");
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        Map<String, Object> env = RestFileSystemOptions.builder()
                .cacheLocation(tempDir)
                .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                .set(RestFileSystemOptions.CacheFallback.OFFLINE)
                .build();

        FileSystem restfs = FileSystems.newFileSystem(uri, env);
        Path pathInRestServer = restfs.getPath("dictionaries/data/FocalPlane/3702060141.ser");
        long time1 = readFile(pathInRestServer);
        // Second time should come from cache
        long time2 = readFile(pathInRestServer);
        Assert.assertTrue(time2<time1);

        ((RestFileSystem)restfs).getCache().setCacheFallbackOption(RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE);
        // Now it should come from cache without trips to the remote server
        long time3 = readFile(pathInRestServer);
        Assert.assertTrue(time3<=time2);

        Path pathInRestServer2 = restfs.getPath("dictionaries/command/FocalPlane/846244239.ser");
        long time4 = readFile(pathInRestServer2);
        //The second file is smaller, so it should take less than the first file
        Assert.assertTrue(time4<time1);
        
        // Now it should come from cache without trips to the remote server
        long time5 = readFile(pathInRestServer2);
        Assert.assertTrue(time5<time4);

        ((RestFileSystem)restfs).getCache().setCacheFallbackOption(RestFileSystemOptions.CacheFallback.OFFLINE);
        // This time it should still come from the cache, but with round trips to the server,
        // so it should be slower than the previous result.
        long time6 = readFile(pathInRestServer2);
        //Since there are trip to the server, this time should be longer than
        //the previous with trips.
        Assert.assertTrue(time5<=time6);
        Assert.assertTrue(time6<time4);
        restfs.close();

        
    }

    private static long readFile(Path pathInRestServer) throws IOException {
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
        long delta = stop - start;
        System.out.printf("Read %d bytes in %dms\n", length, delta);
        return delta;
    }
}
