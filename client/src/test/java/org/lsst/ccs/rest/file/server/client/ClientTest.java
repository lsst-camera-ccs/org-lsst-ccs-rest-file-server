package org.lsst.ccs.rest.file.server.client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.web.rest.file.server.TestServer;

/**
 *
 * @author tonyj
 */
public class ClientTest {

    private static TestServer testServer;
    private static FileSystem restfs;

    public ClientTest() {
    }

    @BeforeAll
    public static void setUpClass() throws URISyntaxException, IOException {
        testServer = new TestServer();
        URI uri = UriBuilder.fromUri(testServer.getServerURI()).scheme("ccs").build();
        restfs = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
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
    public void localPathTest() throws IOException {
        Path path = Paths.get("xyz" + UUID.randomUUID());
        Files.createFile(path);
        Path absolute = path.toAbsolutePath();
        assertNotEquals(path, absolute);
        assertTrue(Files.isSameFile(path, absolute));
        Files.delete(path);
    }

    @Test
    public void restPathTest() throws IOException {
        Path path = restfs.getPath("xyz" + UUID.randomUUID());
        Path absolute = path.toAbsolutePath();
        assertNotEquals(path, absolute);
        assertTrue(Files.isSameFile(path, absolute));
    }

    @Test
    public void localTest() throws IOException {
        Path tempDir = Files.createTempDirectory("rest-test");
        Path localPath = tempDir.resolve("test.txt");
        assertTrue(localPath.isAbsolute());
        final String content = "This is a test file";
        try (BufferedWriter writer = Files.newBufferedWriter(localPath)) {
            writer.append(content);
        }
        standardTest(localPath, content);
    }

    @Test
    public void simpleTest() throws IOException {

        Path pathInRestServer = restfs.getPath("test2.txt");
        assertFalse(Files.exists(pathInRestServer));
        assertFalse(pathInRestServer.isAbsolute());
        pathInRestServer = pathInRestServer.toAbsolutePath();
        final String content = "This is a test file";
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer)) {
            writer.append(content);
        }
        standardTest(pathInRestServer, content);
    }

    private void standardTest(Path path, final String content) throws IOException {
        assertTrue(Files.exists(path));
        final Path parent = path.getParent();
        assertTrue(Files.isDirectory(parent));
        List<Path> files = Files.list(parent).collect(Collectors.toList());
        assertEquals(1, files.size());
        assertEquals(path, files.get(0));
        List<String> lines = Files.lines(path).collect(Collectors.toList());
        assertEquals(1, lines.size());
        assertEquals(content, lines.get(0));

        assertEquals(content.length(), Files.size(path));
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        assertEquals(content.length(), attributes.size());

        assertEquals("text/plain", Files.probeContentType(path));

        Path child = parent.resolve("newDir");
        Files.createDirectory(child);
        Path newPath = child.resolve(path.getFileName());
        Files.move(path, newPath);
        assertFalse(Files.exists(path));
        assertTrue(Files.exists(newPath));

        Files.delete(newPath);
        assertFalse(Files.exists(newPath));
        Files.delete(child);
    }

    @Test
    public void nonFileTest() throws IOException {
        Path pathInRestServer = restfs.getPath("test3.txt");
        try {
            List<String> lines = Files.lines(pathInRestServer).collect(Collectors.toList());
            fail("should not get here!");
        } catch (NoSuchFileException x) {
            // OK, this is expected.
        }
    }

    @Test
    public void rewriteFile() throws IOException {
        Path pathInRestServer = restfs.getPath("test.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer, StandardOpenOption.CREATE_NEW)) {
            writer.append("This is a test file");
        }
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer, StandardOpenOption.CREATE_NEW)) {
                writer.append("This is a test file as well");
            } 
            fail("Should not get here!");
        } catch (FileAlreadyExistsException x) {
            // OK, expected
        }
    }
    
    //@Test
    public void versionTest() throws IOException {

        Path pathInRestServer = restfs.getPath("versioned.txt");
        assertFalse(Files.exists(pathInRestServer));
        final String content = "This is a test file";
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer, VersionOpenOption.CREATE)) {
            writer.append(content);
        }
        assertTrue(Files.exists(pathInRestServer));
        final Path parent = pathInRestServer.getParent();
        assertTrue(Files.isDirectory(parent));
        Files.list(parent).forEach(System.out::println);
        assertEquals(1, Files.list(parent).count());
        List<String> lines = Files.lines(pathInRestServer).collect(Collectors.toList());
        assertEquals(1, lines.size());
        assertEquals(content, lines.get(0));

        assertEquals(content.length(), Files.size(pathInRestServer));
        BasicFileAttributes attributes = Files.readAttributes(pathInRestServer, BasicFileAttributes.class);
        assertEquals(content.length(), attributes.size());

        assertEquals("text/plain", Files.probeContentType(pathInRestServer));

        Path child = parent.resolve("newDir");
        Files.createDirectory(child);
        Path newPath = child.resolve(pathInRestServer.getFileName());
        Files.move(pathInRestServer, newPath);
        assertFalse(Files.exists(pathInRestServer));
        assertTrue(Files.exists(newPath));

        Files.delete(newPath);
        assertFalse(Files.exists(newPath));
        Files.delete(child);
    }
}
