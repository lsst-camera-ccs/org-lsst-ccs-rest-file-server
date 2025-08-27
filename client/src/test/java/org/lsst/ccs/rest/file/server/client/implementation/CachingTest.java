package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;
import org.lsst.ccs.rest.file.server.client.implementation.Cache.CacheEntry;
import org.lsst.ccs.web.rest.file.server.TestServer;

/**
 *
 * @author tonyj
 */
public class CachingTest {

    @Test
    public void cacheTest()  throws URISyntaxException, IOException, InterruptedException {
        cacheTest(9997, RestFileSystemOptions.CacheFallback.OFFLINE);
    }

    @Test
    public void cacheTestWhenPossible()  throws URISyntaxException, IOException, InterruptedException {
        cacheTest(9996, RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE);
    }

    public void cacheTest(int port, RestFileSystemOptions.CacheFallback cacheMode) throws URISyntaxException, IOException, InterruptedException {
        TestServer testServer = new TestServer(port);
        URI restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        final Path tempDir = Files.createTempDirectory("rfs");
        String cacheName = "default";
        Map<String, Object> env = RestFileSystemOptions.builder()
                .cacheLocation(tempDir)
                .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                .set(cacheMode)
                .build();

        final String content = "I wlll be cached!";
        final String fileName = "testCache.txt";

        try (FileSystem restfs = FileSystems.newFileSystem(restRootURI, env)) {
            Path pathInRestServer = restfs.getPath(fileName);
            assertFalse(Files.exists(pathInRestServer));
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer)) {
                writer.append(content);
            }
            RestFileSystem client = (RestFileSystem) restfs;
            Cache cache = client.getCache();
            URI tmpFileUri = client.getURI("rest/download/"+fileName);
            URI fileUri = new URI(tmpFileUri.toString().replace("ccs:", "http:"));
            
            listAndRead(pathInRestServer, content, 1);
            CacheEntry e = cache.getEntry(fileUri,cacheName);
            assertNotNull(e);
            assertEquals(0, e.getUpdateCount());
            
            // Read again, this time should use the cache
            listAndRead(pathInRestServer, content, 1);
            // Wen using WHEN_POSSIBLE, cache is never updated
            assertEquals(cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? 0 : 1, e.getUpdateCount());
            
            // Change the file, and make sure things still work as expected
            // Note, the file cache uses "lastModified" timestamps, which are only accurate to 1 second
            // so need to pause to make sure new file has a different timestamp
            Thread.sleep(1500);
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.append(content + content);
            }
            // Note, when using WHEN_POSSIBLE we always get the old file contents
            String expectedContents = cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? content : content+content;
            listAndRead(pathInRestServer, expectedContents, 1);
            // Note, cache entry will have changed, so need to refetch it
            e = cache.getEntry(fileUri,cacheName);
            assertNotNull(e);
            assertEquals(0, e.getUpdateCount());

            // Read again, this time should use the cache
            listAndRead(pathInRestServer, expectedContents, 1);
            assertEquals(cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? 0 : 1, e.getUpdateCount());
            
            Path pathInRestServer2 = restfs.getPath(fileName + "2");
            // Create a new file, and make sure the directory listing shows it
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer2)) {
                writer.append(content + content);
            }
            // Note, when using WHEN_POSSIBLE, the directory listing will be out of date
            int expectedDirectorySize = cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? 1 : 2;
            listAndRead(pathInRestServer, expectedContents, expectedDirectorySize);
            // In this case cache entry will not have changed, so no need to refetch it.
            assertEquals(cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? 0 : 2, e.getUpdateCount());
            
            // Read again, this time should use the cache
            listAndRead(pathInRestServer, expectedContents, expectedDirectorySize);
            assertEquals(cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? 0 : 3, e.getUpdateCount());
        }
    }

    @Test
    public void offlineCacheTest() throws IOException, URISyntaxException {
        TestServer testServer = new TestServer();
        URI restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        final Path tempDir = Files.createTempDirectory("rfs");
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
            listAndRead(pathInRestServer, content, 1);

            // Shut the server down, so we can test that it works as expected in OFFLINE mode
            testServer.shutdown();
        }

        try (FileSystem restfs2 = FileSystems.newFileSystem(restRootURI, env)) {
            Path pathInRestServer2 = restfs2.getPath(fileName);

            listAndRead(pathInRestServer2, content, 1);

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

    @Test
    public void cacheLockTest() throws URISyntaxException, IOException {
        final Path tempDir = Files.createTempDirectory("rfs");

        TestServer testServer = new TestServer();
        TestServer testServer2 = new TestServer(9998);
        try {
            URI restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
            Map<String, Object> env = RestFileSystemOptions.builder()
                    .cacheLocation(tempDir)
                    .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                    .set(RestFileSystemOptions.CacheFallback.OFFLINE)
                    .build();
            try (Cache c = new Cache(new RestFileSystemOptionsHelper(env), new Properties())) {

                URI restRootURI2 = UriBuilder.fromUri(testServer2.getServerURI()).scheme("ccs").build();
                Map<String, Object> env2 = RestFileSystemOptions.builder()
                        .cacheLocation(tempDir)
                        .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                        .set(RestFileSystemOptions.CacheFallback.OFFLINE)
                        .build();
                try {

                    Cache c1 = new Cache(new RestFileSystemOptionsHelper(env2), new Properties());
                    fail();
                } catch (IOException x) {
                    assertTrue(x.getMessage().contains("in use"));
                }

                env2.put(RestFileSystemOptions.ALLOW_ALTERNATE_CACHE_LOCATION, true);
                try (Cache c2 = new Cache(new RestFileSystemOptionsHelper(env2), new Properties())) {
                    // We should get here
                }
            }
        } finally {
            testServer.shutdown();
            testServer2.shutdown();
        }
    }

    private void listAndRead(Path path, String content, int expectedListSize) throws IOException {
        assertTrue(Files.exists(path));
        final Path parent = path.getParent();
        assertTrue(Files.isDirectory(parent));
        List<Path> files = Files.list(parent).collect(Collectors.toList());
        assertEquals(expectedListSize, files.size());
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
