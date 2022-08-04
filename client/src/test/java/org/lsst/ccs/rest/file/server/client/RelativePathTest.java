package org.lsst.ccs.rest.file.server.client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import javax.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.web.rest.file.server.TestServer;

/**
 *
 * @author tonyj
 */
public class RelativePathTest {

    private static TestServer testServer;
    private static FileSystem restfs;
    private static URI restRootURI;
    
    @BeforeAll
    public static void setUpClass() throws URISyntaxException, IOException {
        testServer = new TestServer();
        restRootURI = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        restfs = FileSystems.newFileSystem(restRootURI, Collections.<String, Object>emptyMap());
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        restfs.close();
        testServer.shutdown();
    }

    @AfterEach
    public void cleanup() throws IOException {
        testServer.cleanFiles();
    }

    @Test
    public void relativePathTest() throws IOException {
        Path pathInRestServer = restfs.getPath("misc/test2.txt");
        assertFalse(Files.exists(pathInRestServer));
        assertFalse(pathInRestServer.isAbsolute());
        pathInRestServer = pathInRestServer.toAbsolutePath();
        final String content = "This is a test file";
        Files.createDirectory(pathInRestServer.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer)) {
            writer.append(content);
        }
        assertTrue(Files.exists(pathInRestServer));
        
        // Should trailing / be required?
        URI relativeRootURI = restRootURI.resolve("misc/");
        System.out.println(restRootURI);
        System.out.println(relativeRootURI);
        FileSystem relativefs = FileSystems.newFileSystem(relativeRootURI, Collections.<String, Object>emptyMap());
        Path relativePathInRestServer = relativefs.getPath("test2.txt");
        System.out.println(relativePathInRestServer);
        assertTrue(Files.exists(relativePathInRestServer));
        
    }
    
    @Test
    public void shouldFailTest() throws IOException, URISyntaxException {
        try {
            FileSystems.newFileSystem(new URI("ccs://lsst-camera.slac.stanford.edu/RestFileServer"), Collections.<String, Object>emptyMap());
            fail("Should not get here");
        } catch (IOException x) {
            // Expected
        }
    }

    
    
}
