package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * Cross-JVM cache-lock guard (ADR 0003 §3). A single JVM cannot produce a
 * genuine {@code tryLock()==null}; same-JVM contention always surfaces as an
 * {@code OverlappingFileLockException} (the share path, covered by
 * {@code CachingTest}). So this launches a real second JVM
 * ({@link CacheLockHolder}) that holds the lock on a location, then asserts this
 * JVM either spills to {@code <loc>-1} (spill enabled) or fails "in use" (spill
 * disabled). Linux-only; revisit if it proves flaky in CI.
 */
public class CacheLockCrossJvmTest {

    private Process holder;

    @AfterEach
    public void tearDown() {
        if (holder != null) {
            holder.destroyForcibly();
            holder = null;
        }
        RestFileSystemOptionsHelper.resetGlobalCacheConfigForTest();
    }

    @Test
    public void spillsToAlternateWhenAnotherProcessHoldsTheLock() throws Exception {
        final Path tempDir = Files.createTempDirectory("rfs");
        final Path primary = tempDir.resolve("cache");
        startHolder(primary);

        // Spill enabled: this JVM must land on a sibling <loc>-N, not the primary.
        RestFileSystemOptionsHelper.setGlobalCacheConfigForTest(primary, true);
        RestFileSystemOptionsHelper options = new RestFileSystemOptionsHelper(
                RestFileSystemOptions.builder().set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK).build());
        try (Cache cache = new Cache(options)) {
            assertNotEquals(primary.toAbsolutePath(), cache.getDiskCacheLocation().toAbsolutePath(),
                    "should have spilled off the locked primary location");
            assertEquals(primary.resolveSibling(primary.getFileName() + "-1").toAbsolutePath(),
                    cache.getDiskCacheLocation().toAbsolutePath());
        }
    }

    @Test
    public void failsInUseWhenSpillDisabled() throws Exception {
        final Path tempDir = Files.createTempDirectory("rfs");
        final Path primary = tempDir.resolve("cache");
        startHolder(primary);

        // Spill disabled: this JVM must fail rather than spill.
        RestFileSystemOptionsHelper.setGlobalCacheConfigForTest(primary, false);
        RestFileSystemOptionsHelper options = new RestFileSystemOptionsHelper(
                RestFileSystemOptions.builder().set(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK).build());
        try {
            new Cache(options);
            fail("expected 'in use' failure while another process holds the lock");
        } catch (IOException x) {
            assertTrue(x.getMessage().contains("in use"), x.getMessage());
        }
    }

    /**
     * Launches the lock-holder JVM on {@code location} and blocks until it prints
     * its readiness marker (or fails/ times out).
     */
    private void startHolder(Path location) throws IOException, InterruptedException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", System.getProperty("java.class.path"),
                CacheLockHolder.class.getName(), location.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        holder = pb.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        BufferedReader reader = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(CacheLockHolder.READY_MARKER)) {
                return;
            }
            if (System.nanoTime() > deadline) {
                break;
            }
        }
        fail("lock-holder process did not become ready");
    }
}
