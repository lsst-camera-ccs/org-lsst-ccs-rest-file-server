package org.lsst.ccs.web.rest.file.server;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author tonyj
 */
public class VersionedFileTest {

    private static Path tempDir;

    @BeforeAll
    public static void setUpClass() throws IOException {
        tempDir = Files.createTempDirectory("versions");
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void genericTest() throws IOException, DiffException {
        String content = "Just Testing";
        String content2 = "Just Testing some more";
        VersionedFile vf = VersionedFile.create(tempDir.resolve("test.file"), content.getBytes());
        assertEquals(1, vf.getDefaultVersion());
        assertEquals(1, vf.getLatestVersion());
        assertFalse(vf.isHidden(1));
        Path file = vf.getDefault();
        assertEquals(content, new String(Files.readAllBytes(file)));
        int nv = vf.addVersion(content2.getBytes(), false);
        assertEquals(2, nv);
        assertEquals(1, vf.getDefaultVersion());
        assertEquals(2, vf.getLatestVersion());
        assertFalse(vf.isHidden(2));
        int[] versions = vf.getVersions();
        assertEquals(2, versions.length);
        vf.setDefaultVersion(2);
        assertEquals(2, vf.getDefaultVersion());
        assertEquals(2, vf.getLatestVersion());

        Path file1 = vf.getPathForVersion(1);
        Path file2 = vf.getPathForVersion(2);
        List<String> lines1 = Files.readAllLines(file1);
        List<String> lines2 = Files.readAllLines(file2);
        Patch<String> diff = DiffUtils.diff(lines1, lines2);
        List<AbstractDelta<String>> deltas = diff.getDeltas();
        assertEquals(1, deltas.size());
        assertEquals(content, deltas.get(0).getSource().getLines().get(0));
        assertEquals(content2, deltas.get(0).getTarget().getLines().get(0));
        List<String> generateUnifiedDiff = UnifiedDiffUtils.generateUnifiedDiff("version1", "version2", lines1, diff, 2);
        assertEquals("-" + content, generateUnifiedDiff.get(3));
        assertEquals("+" + content2, generateUnifiedDiff.get(4));

        int nv2 = vf.addVersion(content2.getBytes(), true);
        assertEquals(2, nv2);
    }
    
    @Test void testHiddenVersions() throws IOException {
        String content = "Just Testing";
        VersionedFile vf = VersionedFile.create(tempDir.resolve("test2.file"), content.getBytes());
        int nv1 = vf.addVersion(content.getBytes(), false);
        int nv2 = vf.addVersion(content.getBytes(), false);
        vf.setHidden(nv1, true);
        assertTrue(vf.isHidden(nv1));
        Assert.assertArrayEquals(new int[]{vf.getDefaultVersion(), nv2}, vf.getVersions(false));
        vf.setHidden(nv1, false);
        assertFalse(vf.isHidden(nv1));       
        try {
            vf.setHidden(nv2, true);
            fail("Should not get here");
        } catch (RuntimeException x) {
            // expected
        }
        try {
            vf.setHidden(vf.getDefaultVersion(), true);
            fail("Should not get here");
        } catch (RuntimeException x) {
            // expected
        }
    }

    @Test
    public void testConvert() throws IOException {
        String content = "Unversioned Content";
        Path testFile = tempDir.resolve("fileToConvert.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(testFile)) {
            writer.write(content);
        }
        assertTrue(Files.exists(testFile));
        VersionedFile vf = VersionedFile.convert(testFile);
        assertEquals(1, vf.getDefaultVersion());
        assertEquals(1, vf.getLatestVersion());
    }
}
