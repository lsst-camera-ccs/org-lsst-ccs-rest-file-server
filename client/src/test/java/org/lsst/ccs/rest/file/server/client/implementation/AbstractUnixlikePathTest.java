package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author tonyj
 */
public class AbstractUnixlikePathTest {

    @Test
    public void rootTest() {
        UnixPath.Builder builder = new UnixPath.Builder();
        Path root = builder.getPath("/");
        assertEquals(root, root.getRoot());
    }

    @Test
    public void normalizeTest() {
        UnixPath.Builder builder = new UnixPath.Builder();
        Path abc = builder.getPath("a/b/c");
        assertEquals(abc, abc.normalize());

        Path withDots = builder.getPath("a/./c");
        assertEquals("a/c", withDots.normalize().toString());

        Path withDoubleDots = builder.getPath("a/../c");
        assertEquals("c", withDoubleDots.normalize().toString());

        Path startWithDoubleDots = builder.getPath("../../c");
        assertEquals("../../c", startWithDoubleDots.normalize().toString());

        Path moreDoubleDots = builder.getPath("/../../c");
        assertEquals("/../../c", moreDoubleDots.normalize().toString());
    }

    @Test
    public void relativizeTest() throws IOException {
        UnixPath.Builder builder = new UnixPath.Builder();

        Path path1 = builder.getPath("a", "b", "c");
        Path path2 = builder.getPath("a");
        Path path3 = path2.relativize(path1);
        assertEquals("b/c", path3.toString());
        Path path4 = path1.relativize(path2);
        assertEquals("../..", path4.toString());
    }
}
