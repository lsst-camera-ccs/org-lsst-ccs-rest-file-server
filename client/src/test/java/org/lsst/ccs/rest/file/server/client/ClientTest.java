package org.lsst.ccs.rest.file.server.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static URI restRootURI;

    public ClientTest() {
    }

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
        assertTrue(Files.isSameFile(path, files.get(0)));
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
        } catch (NoSuchFileException | FileNotFoundException x) {
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

    @Test
    public void simpleVersionTest() throws IOException {

        Path pathInRestServer = restfs.getPath("versioned.txt");
        assertFalse(Files.exists(pathInRestServer));
        final String content = "This is a test file";
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer, VersionOpenOption.LATEST)) {
            writer.append(content);
        }
        standardTest(pathInRestServer, content);
    }

    @Test
    public void extendedVersionTest() throws IOException {

        Path pathInRestServer = restfs.getPath("versioned.txt");
        assertFalse(Files.exists(pathInRestServer));
        final String content = "This is a test file";
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer, VersionOpenOption.LATEST)) {
            writer.append(content);
        }
        BasicFileAttributes basicAttributes = Files.readAttributes(pathInRestServer, BasicFileAttributes.class);
        assertFalse(basicAttributes.isDirectory());
        VersionedFileAttributes versionAttributes = Files.readAttributes(pathInRestServer, VersionedFileAttributes.class);
        assertTrue(versionAttributes.getDefaultVersion() == 1);
        
        VersionedFileAttributeView versionView = Files.getFileAttributeView(pathInRestServer, VersionedFileAttributeView.class);
        assertTrue(versionView.readAttributes().getDefaultVersion() == 1);
        assertTrue(versionView.readAttributes().getLatestVersion() == 1);
        
        final String newContent = "This is some new content";
        try (BufferedWriter writer = Files.newBufferedWriter(pathInRestServer)) {
            writer.append(newContent);
        }
        assertTrue(versionView.readAttributes().getDefaultVersion() == 1);
        assertTrue(versionView.readAttributes().getLatestVersion() == 2);

        versionView.setDefaultVersion(2);
        assertTrue(versionView.readAttributes().getDefaultVersion() == 2);
        assertTrue(versionView.readAttributes().getLatestVersion() == 2);
        
        assertFalse(versionView.readAttributes().isHidden(1));
        assertEquals("", versionView.readAttributes().getComment(1));
        
        versionView.setComment(2, "This is a comment");
        versionView.setHidden(1, true);

        assertEquals("This is a comment", versionView.readAttributes().getComment(2));
        assertTrue(versionView.readAttributes().isHidden(1));
        
        try {
            versionView.setHidden(2, true);
            fail("Should not get here");
        } catch (IOException x) {
            // expected
        }
        
        try (InputStream in = Files.newInputStream(pathInRestServer, VersionedOpenOption.DIFF, VersionOpenOption.of(2), VersionOpenOption.of(1));
                BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            assertEquals(5, lines.size());
            assertEquals("-" + content, lines.get(3));
            assertEquals("+" + newContent, lines.get(4));
        }

        try (InputStream in = Files.newInputStream(pathInRestServer, VersionedOpenOption.DIFF);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            assertEquals(5, lines.size());
            assertEquals("-" + content, lines.get(3));
            assertEquals("+" + newContent, lines.get(4));
        }

        try (InputStream in = Files.newInputStream(pathInRestServer, VersionedOpenOption.DIFF, VersionOpenOption.of(1))) {
            fail("Should not get here");
        } catch (IOException x) {
            // Expected
        }
    }

    @Test
    public void pathTest() throws IOException {
        Path path = Paths.get(restRootURI.resolve("rhubarb/test.file"));
        assertEquals("ccs", path.toUri().getScheme());
        assertEquals("test.file", path.getFileName().toString());

        // FIXME: Should be moved elsewhere, depends on external URL
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/newTest.properties");
        Path path2 = Paths.get(uri);
        System.out.println(path2.toUri());
        BasicFileAttributes bfa = Files.readAttributes(path2, BasicFileAttributes.class);
        System.out.println(bfa.isOther());
    }

//    @Test 
//    public void globTest() throws IOException {
//        FileSystem fs = FileSystems.getDefault();
//        //PathMatcher pathMatcher = fs.getPathMatcher("/home/tonyj/Data/*.ser");
//        Path dir = fs.getPath("/home/tonyj/Data/");
//        Files.newDirectoryStream(dir, "*.ser").forEach(System.out::println);
//    }
    @Test
    public void relativizeTest() throws IOException {
        //FileSystem defaultFileSystem = FileSystems.getDefault();
        FileSystem fs = restfs;

        Path path1 = fs.getPath("a", "b", "c");
        Path path2 = fs.getPath("a");
        Path path3 = path2.relativize(path1);
        assertEquals("b/c", path3.toString());
        Path path4 = path1.relativize(path2);
        assertEquals("../..", path4.toString());
    }
}
