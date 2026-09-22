package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.lsst.ccs.bootstrap.BootstrapResourceUtils;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * THROWAWAY: reads filter.spec from the remote REST file server by pointing the
 * CCS resource path at a ccs:// directory, the way the image-utilities subsystem
 * does (system.property.org.lsst.ccs.resource.path=ccs://.../spec-files-combined[spec]).
 *
 * Reproduces the production JVM condition where DEFAULT_ENV_PROPERTY is set (as
 * the bootstrap does at launch) so EVERY ccs:// mount defaults to MEMORY_AND_DISK.
 * Before the getPath offline-eviction fix this failed: the root-discovery
 * heuristic latched the too-short prefix ccs://host/ that only "succeeded" by
 * falling offline. CacheLocation points at a temp dir so the real ~/ccs/cache
 * is never touched.
 *
 * Depends on the live dev server, so not portable / not for CI. Placed in the
 * .implementation package so @AfterAll can reset the memoized JVM-global cache
 * config, letting it coexist with the rest of the (shared-JVM) suite.
 */
public class RemoteFilterSpecLoadingTest {

    private static final String SPEC_DIR =
            "ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/misc/spec-files-combined[spec]";
    private static final URI REST_ROOT =
            URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");

    @BeforeAll
    public static void setUpJvmGlobalCacheEnvironment() throws Exception {
        // Must be set before any ccs:// file system is created: the JVM-global
        // cache config is resolved once and memoized.
        Path cacheDir = Files.createTempDirectory("filter-spec-cache");
        String defaultEnv = "{\"CacheOptions\":\"MEMORY_AND_DISK\","
                + "\"CacheFallbackLocation\":true,"
                + "\"CacheLocation\":\"" + cacheDir.toString().replace("\\", "\\\\") + "\"}";
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY, defaultEnv);
        System.setProperty("org.lsst.ccs.resource.path", SPEC_DIR);
        System.setProperty("org.lsst.ccs.distribution.path", "");
    }

    @AfterAll
    public static void tearDownJvmGlobalState() throws Exception {
        // Close the ccs:// file system the bootstrap resource load created for
        // the server root, so a later test does not hit FileSystemAlreadyExists.
        try {
            FileSystem fs = FileSystems.getFileSystem(REST_ROOT);
            fs.close();
        } catch (Exception ignore) {
            // never created / already closed
        }
        // Undo the memoized JVM-global cache config and release the disk-cache lock.
        RestFileSystemOptionsHelper.resetGlobalCacheConfigForTest();
        // Undo the system properties we set.
        System.clearProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY);
        System.clearProperty("org.lsst.ccs.resource.path");
        System.clearProperty("org.lsst.ccs.distribution.path");
    }

    @Test
    public void testGetBootstrapResourceReadsRemoteFilterSpec() throws Exception {
        try (InputStream in = BootstrapResourceUtils.getBootstrapResource("filter.spec")) {
            assertNotNull(in, "filter.spec not found on the remote resource path");
            String content = new BufferedReader(new InputStreamReader(in))
                    .lines().collect(Collectors.joining("\n"));
            assertTrue(content.contains("FILTBAND"),
                    "remote filter.spec did not contain expected keyword FILTBAND");
        }
    }
}
