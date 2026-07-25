package org.lsst.ccs.rest.file.server.client.implementation;

import java.nio.file.Paths;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * Test helper launched as a separate JVM by {@link CacheLockCrossJvmTest}. It
 * opens <em>two</em> {@code MEMORY_AND_DISK} {@link Cache} instances at the
 * location given in argv[0], takes the cross-JVM lock, prints a readiness marker,
 * then blocks until the parent kills it. A safety timeout guarantees it cannot
 * linger and hold the lock forever if the parent fails to tear it down.
 * <p>
 * It opens two caches on purpose: this models a real multi-mount JVM (a shell or
 * the toolkit's three services) and is the [ADR 0004] regression guard. Under the
 * old per-{@code Cache} locking, the second mount's {@code close()} silently
 * dropped the lock the first still believed it held, so this holder would NOT
 * actually exclude the parent JVM — the exact defect that shipped. A single-mount
 * holder (the previous version) never triggered it, which is why the bug slipped
 * past the cross-JVM tests.
 */
public class CacheLockHolder {

    /** Printed on stdout once the lock is held, so the parent can proceed. */
    static final String READY_MARKER = "LOCK_HELD";

    public static void main(String[] args) throws Exception {
        String location = args[0];
        // Deliver the location the same way production does: the global config
        // property. Spill off, so this process takes the primary location.
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY,
                "{\"CacheOptions\":\"MEMORY_AND_DISK\",\"CacheLocation\":\"" + location + "\"}");

        RestFileSystemOptionsHelper options = new RestFileSystemOptionsHelper(null);
        // Two mounts in one JVM — the multi-mount case (see class doc).
        try (Cache cache = new Cache(options); Cache cache2 = new Cache(options)) {
            // Confirm we actually landed on the requested location (no spill) and
            // that the second mount shares it rather than spilling.
            if (!Paths.get(location).toAbsolutePath().equals(cache.getDiskCacheLocation().toAbsolutePath())
                    || !cache.getDiskCacheLocation().equals(cache2.getDiskCacheLocation())) {
                System.out.println("UNEXPECTED_LOCATION " + cache.getDiskCacheLocation() + " / " + cache2.getDiskCacheLocation());
                return;
            }
            System.out.println(READY_MARKER);
            System.out.flush();
            // Hold the lock until killed, with a safety cap so a leaked process
            // releases the lock on its own.
            Thread.sleep(60_000);
        }
    }
}
