package org.lsst.ccs.rest.file.server.client;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.web.rest.file.server.TestServer;

/**
 *
 * @author tonyj
 */
public class CachingTest {

    @Test
    public void cacheTest() throws IOException, URISyntaxException {
        TestServer testServer = new TestServer();
        URI restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        final File tempDir = Files.createTempDirectory("rfs").toFile();
        Map<String, Object> env = RestFileSystemOptions.builder()
                .cacheLocation(tempDir)
                .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                .set(RestFileSystemOptions.CacheFallback.OFFLINE)
                .build();

        final String content = "I wlll be cached!";
        final String fileName = "testCache.txt";

        try (FileSystem restfs = FileSystems.newFileSystem(restRootURI, env)) {

            Path pathInRestServer = restfs.getPath(fileName);
            assertFalse(Files.exists(pathInRestServer));
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer)) {
                writer.append(content);
            }
            listAndRead(pathInRestServer, content);

            testServer.shutdown();
        }

        try (FileSystem restfs2 = FileSystems.newFileSystem(restRootURI, env)) {
            Path pathInRestServer2 = restfs2.getPath(fileName);

            listAndRead(pathInRestServer2, content);

            Path someOtherPath = restfs2.getPath("someOther.txt");
            assertFalse(Files.exists(someOtherPath));
            try {
                Files.getLastModifiedTime(someOtherPath);
                fail("Should not get here");
            } catch (IOException x) {
                // Expected
            }

            try {
                try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer2)) {
                    writer.append(content);
                }
                fail("Should not get here");
            } catch (IOException x) {
                // Expected
            }

            try {
                Files.delete(pathInRestServer2);
                fail("Should not get here");
            } catch (IOException x) {
                // Expected
            }
        }
    }

    private void listAndRead(Path path, String content) throws IOException {
        assertTrue(Files.exists(path));
        final Path parent = path.getParent();
        assertTrue(Files.isDirectory(parent));
        List<Path> files = Files.list(parent).collect(Collectors.toList());
        assertEquals(1, files.size());
        assertTrue(Files.isSameFile(path, files.get(0)));
        List<String> lines = Files.lines(path).collect(Collectors.toList());
        assertEquals(1, lines.size());
        assertEquals(content, lines.get(0));

        assertEquals(content.length(), Files.size(path));
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        assertEquals(content.length(), attributes.size());

        assertEquals("text/plain", Files.probeContentType(path));
    }
}
