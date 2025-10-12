package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.BufferedWriter;
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
import org.junit.Assert;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import org.lsst.ccs.rest.file.server.client.implementation.Cache.CacheEntry;
import org.lsst.ccs.web.rest.file.server.TestServer;

/**
 * Test support for versioned files.
 * 
 */
public class VersionedFileTest {

    
    @Test
    public void fileWithVersionTest() throws IOException {
        RestPath path = new RestPath(null, "/file(7).txt");
        Assert.assertTrue(path.isVersionedFile());
        Assert.assertEquals("7", path.getVersion());
        Assert.assertEquals("file.txt", path.getRestPath());
        
        
        path = new RestPath(null, "/anotherFile.txt(d)");
        Assert.assertTrue(path.isVersionedFile());
        Assert.assertEquals("default", path.getVersion());
        Assert.assertEquals("anotherFile.txt", path.getRestPath());
        
        
    }
    
    
//    @Test
    public void cacheOfflineTest()  throws URISyntaxException, IOException, InterruptedException {
        cacheTest(9997, RestFileSystemOptions.CacheFallback.OFFLINE);
    }

    @Test
    public void cacheWhenPossibleTest()  throws URISyntaxException, IOException, InterruptedException {
        cacheTest(9997, RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE);
    }

    public void cacheTest(int port, RestFileSystemOptions.CacheFallback cacheMode) throws URISyntaxException, IOException, InterruptedException {
        TestServer testServer = new TestServer(port);
        URI restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        final Path tempDir = Files.createTempDirectory("rfs");
        Map<String, Object> env = RestFileSystemOptions.builder()
                .cacheLocation(tempDir)
                .set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK)
                .set(cacheMode)
                .build();

        final String content = "Some Content";
        final String fileName = "testVersion.txt";

        try (FileSystem restfs = FileSystems.newFileSystem(restRootURI, env)) {
            
            //Write a versioned file
            Path pathInRestServer = restfs.getPath(fileName);
            assertFalse(Files.exists(pathInRestServer));
            VersionOpenOption voo = VersionOpenOption.of(1);
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer,voo)) {
                writer.append(content);
            }
            assertTrue(Files.exists(pathInRestServer));

            RestFileSystem client = (RestFileSystem) restfs;
            Cache cache = client.getCache();
            URI tmpFileUri = client.getURI("rest/version/download/"+fileName+"?version=default");
            URI fileUri = new URI(tmpFileUri.toString().replace("ccs:", "http:"));
            
            listAndRead(pathInRestServer, content, 1);
            CacheEntry e = cache.getEntry(fileUri,"default");
            assertNotNull(e);
            assertEquals(0, e.getUpdateCount());
            
            // Read again, this time should use the cache
            listAndRead(pathInRestServer, content, 1);
            // Wen using WHEN_POSSIBLE, cache is never updated
            assertEquals(cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? 0 : 1, e.getUpdateCount());

            Thread.sleep(1000L);

            //Write a new version of the file:
            voo = VersionOpenOption.of(2);
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer,voo, VersionOpenOption.DEFAULT)) {
                writer.append(content).append(content);
            }
            
            Path versionedPathInRestServer = restfs.getPath(fileName+"(2)");
            //Reading again
            listAndRead(versionedPathInRestServer, content+content, 1);
 
            VersionedFileAttributes attributes = Files.readAttributes(versionedPathInRestServer, VersionedFileAttributes.class);
            Assert.assertEquals(1, attributes.getDefaultVersion());
            Assert.assertEquals(2, attributes.getLatestVersion());
           
            //Make version 2 the default
            ((RestFileSystem)restfs).getClient().getVersionedAttributeView((RestPath)restfs.getPath(fileName),null).setDefaultVersion(2);
            versionedPathInRestServer = restfs.getPath(fileName+"(d)");
            //Reading again after changing the default.
            //When using WHEN_POSSIBLE, the default version is cached            

            attributes = Files.readAttributes(versionedPathInRestServer, VersionedFileAttributes.class);
            Assert.assertEquals(2, attributes.getDefaultVersion());
            Assert.assertEquals(2, attributes.getLatestVersion());
            
            
            listAndRead(versionedPathInRestServer, cacheMode == RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE ? content : content+content, 1, (content+content).length());
            
        }
        testServer.shutdown();
    }

    private void listAndRead(Path path, String content, int expectedListSize) throws IOException {
        listAndRead(path,content,expectedListSize, content.length());
    }
    private void listAndRead(Path path, String content, int expectedListSize, long expectedSize) throws IOException {
        assertTrue(Files.exists(path));
        final Path parent = path.getParent();
        assertTrue(Files.isDirectory(parent));
        List<Path> files = Files.list(parent).collect(Collectors.toList());
        assertEquals(expectedListSize, files.size());
        
        //assertTrue(Files.isSameFile(path, files.get(0)));
        List<String> lines = Files.lines(path).collect(Collectors.toList());
        assertEquals(1, lines.size());
        assertEquals(content, lines.get(0));

        assertEquals(expectedSize, Files.size(path));
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        assertEquals(expectedSize, attributes.size());

        assertEquals("text/plain", Files.probeContentType(path));
    }
}
