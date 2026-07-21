package org.lsst.ccs.rest.file.server.client.implementation;

import java.nio.file.Paths;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * Test helper launched as a separate JVM by {@link CacheLockCrossJvmTest}. It
 * opens a {@code MEMORY_AND_DISK} {@link Cache} at the location given in argv[0],
 * which takes the cross-JVM lock, prints a readiness marker, then blocks until
 * the parent kills it. A safety timeout guarantees it cannot linger and hold the
 * lock forever if the parent fails to tear it down.
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
        try (Cache cache = new Cache(options)) {
            // Confirm we actually landed on the requested location (no spill).
            if (!Paths.get(location).toAbsolutePath().equals(cache.getDiskCacheLocation().toAbsolutePath())) {
                System.out.println("UNEXPECTED_LOCATION " + cache.getDiskCacheLocation());
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
