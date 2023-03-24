package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.junit.Assert;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    public void cacheWhenPossibleTest() throws IOException, URISyntaxException {
        runCacheTest(RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE);        
    }
    
    @Test
    public void cacheOfflineTest() throws IOException, URISyntaxException {
        runCacheTest(RestFileSystemOptions.CacheFallback.OFFLINE);        
    }
    
    private void runCacheTest(RestFileSystemOptions.CacheFallback cacheFallback ) throws IOException, URISyntaxException {
        TestServer testServer = new TestServer();
        URI restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        final Path tempDir = Files.createTempDirectory("rfs");
        Map<String, Object> env = RestFileSystemOptions.builder()
                .cacheLocation(tempDir)
                .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                .set(cacheFallback)
                .build();

        final String content = "I wlll be cached!";
        final String fileName = System.currentTimeMillis()+"test2Cache.txt";

        
        try ( FileSystem restfs = FileSystems.newFileSystem(restRootURI, env)) {
            
            RestFileSystem client = (RestFileSystem) restfs;
            Cache cache = client.getCache();
            
            
            Path pathInRestServer = restfs.getPath(fileName);
            
            URI tmpFileUri = client.getURI("rest/download/"+fileName);
            URI fileUri = new URI(tmpFileUri.toString().replace("ccs:", "http:"));
            Assert.assertNull(cache.getEntry(fileUri));
            
            assertFalse(Files.exists(pathInRestServer));
            try ( BufferedWriter writer = Files.newBufferedWriter(pathInRestServer)) {
                writer.append(content);
            }
            listAndRead(pathInRestServer, content);
            CacheEntry e = cache.getEntry(fileUri);
            Assert.assertNotNull(e);
            
            long lastUpdated = e.getLastUpdated();
            listAndRead(pathInRestServer, content);
            
            if ( cacheFallback == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ) {
                //In this case the cache should not have been updated.
                Assert.assertEquals(lastUpdated, e.getLastUpdated());
            } else if ( cacheFallback == RestFileSystemOptions.CacheFallback.OFFLINE ) {
                Assert.assertNotEquals(lastUpdated, e.getLastUpdated());                
            }
            lastUpdated = e.getLastUpdated();
            
            
            try ( InputStream is = client.provider().newInputStream(pathInRestServer,StandardOpenOption.READ)) {
                
            } catch (Exception ex) {
                
            }
            if ( cacheFallback == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ) {
                //In this case the cache should not have been updated.
                Assert.assertEquals(lastUpdated, e.getLastUpdated());
            } else if ( cacheFallback == RestFileSystemOptions.CacheFallback.OFFLINE ) {
                Assert.assertNotEquals(lastUpdated, e.getLastUpdated());                
            }

            testServer.shutdown();
        }

        try ( FileSystem restfs2 = FileSystems.newFileSystem(restRootURI, env)) {
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
                try ( BufferedWriter writer = Files.newBufferedWriter(pathInRestServer2)) {
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
            try (FileSystem restfs = FileSystems.newFileSystem(restRootURI, env)) {

                URI restRootURI2 = UriBuilder.fromUri(testServer2.getServerURI()).scheme("ccs").build();
                Map<String, Object> env2 = RestFileSystemOptions.builder()
                        .cacheLocation(tempDir)
                        .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                        .set(RestFileSystemOptions.CacheFallback.OFFLINE)
                        .build();
                try {
                    FileSystems.newFileSystem(restRootURI2, env2);
                    fail();
                } catch (IOException x) {
                    assertTrue(x.getMessage().contains("in use"));
                }

                env2.put(RestFileSystemOptions.ALLOW_ALTERNATE_CACHE_LOCATION, true);
                try (FileSystem restfs2 = FileSystems.newFileSystem(restRootURI2, env2)) {
                    // We should get here
                }
            }
        } finally {
            testServer.shutdown();
            testServer2.shutdown();
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
