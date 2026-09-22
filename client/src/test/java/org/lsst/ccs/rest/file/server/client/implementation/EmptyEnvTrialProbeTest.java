package org.lsst.ccs.rest.file.server.client.implementation;

import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * Pins down the regression that stopped a CCS subsystem loading its {@code .seq}
 * files after the {@code rest-file-server} client was bumped from 1.1.8 to
 * 1.1.10/1.1.11 (toolkit commit "Adopt shared-per-JVM cache"), and guards the
 * fix.
 *
 * <h2>How the {@code [seq]} entry is resolved</h2>
 * A resource-path entry such as
 * {@code ccs://host/RestFileServer/misc/sequencer-files[seq]} is turned by
 * bootstrap into {@code Files.exists(Paths.get(uri))}. {@code Paths.get(URI)}
 * for a {@code ccs://} URI dispatches to
 * {@link RestFileSystemProvider#getPath(URI)}, whose trial heuristic opens a
 * file system for successive path prefixes and takes the first that succeeds:
 * <pre>
 *   path = ["RestFileServer","misc","sequencer-files"]
 *   i = 0: trialURI = ccs://host/                    // bare host root
 *   i = 1: trialURI = ccs://host/RestFileServer/     // the real mount root
 *   ...
 * </pre>
 * The bare host root ({@code i == 0}) is <b>not</b> a valid REST endpoint in
 * production (the server is deployed under the {@code /RestFileServer/} servlet
 * context, so {@code http://host/rest/list/} does not answer). That trial is
 * therefore <em>expected to throw</em> so the loop advances to the correct root.
 *
 * <h2>The regression and the fix</h2>
 * Whether an unreachable probe throws or silently succeeds in offline mode is
 * decided by its {@code CacheOptions}/{@code CacheFallback}:
 * <ul>
 *   <li>{@code NONE} / {@code NEVER} &rarr; {@code computeRestURI} rethrows the
 *       connection failure &rarr; the loop advances. (correct)</li>
 *   <li>{@code MEMORY_AND_DISK} / {@code OFFLINE} &rarr; {@code computeRestURI}
 *       treats the unreachable endpoint as a reason to go offline and
 *       <em>returns a file system</em> &rarr; the loop stops at the wrong (bare)
 *       root and the {@code .seq} path is resolved against it. (the bug)</li>
 * </ul>
 * The toolkit installs {@code CacheOptions=MEMORY_AND_DISK} JVM-globally via the
 * {@link RestFileSystemOptions#DEFAULT_ENV_PROPERTY} system property (ADR 0003).
 * In 1.1.10/1.1.11 the trial loop passed a {@code null} env, so every probe
 * inherited that global default and went offline against an unreachable root.
 * The fix has the loop pass an explicit fail-fast env
 * ({@code CacheOptions=NONE}, {@code CacheFallback=NEVER}), so probes fail fast
 * regardless of the global default.
 *
 * @author LSST CCS Team
 */
public class EmptyEnvTrialProbeTest {

    /**
     * The default environment the toolkit installs at JVM startup (ADR 0003).
     */
    private static final String TOOLKIT_DEFAULT_ENV =
            "{\"CacheOptions\":\"MEMORY_AND_DISK\",\"CacheFallback\":\"OFFLINE\"}";

    @AfterEach
    public void clearDefaults() {
        System.clearProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY);
        RestFileSystemProvider.setDefaultFileSystemOption(null);
        RestFileSystemOptionsHelper.resetGlobalCacheConfigForTest();
    }

    /**
     * End to end through {@code getPath} (no external network), with the toolkit
     * default installed. Every prefix of a multi-segment {@code ccs://} URI whose
     * host is unreachable must fail to open, so {@code getPath} must find no
     * usable root and throw {@link FileSystemNotFoundException}.
     * <p>
     * While the regression was present, the {@code i == 0} bare-root probe
     * inherited {@code MEMORY_AND_DISK}/{@code OFFLINE} from the global default,
     * went offline instead of throwing, and {@code getPath} returned a
     * {@link Path} bound to that wrong root rather than throwing — which is how a
     * {@code .seq} file ended up sought in the wrong place even though the server
     * was up and the file existed. The fix makes the probes fail fast, restoring
     * the throw.
     */
    @Test
    public void getPathMustFailFastAgainstUnreachableHostDespiteGlobalDefault() {
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY, TOOLKIT_DEFAULT_ENV);

        // Port 1 is not listening: every prefix probe fails to connect, exactly
        // as http://host/rest/list/ (no servlet context) does in production for
        // the bare-root prefix.
        URI unreachable = URI.create("ccs://127.0.0.1:1/RestFileServer/misc/sequencer-files/seq1.seq");

        RestFileSystemProvider provider = new RestFileSystemProvider();
        assertThrows(FileSystemNotFoundException.class,
                () -> provider.getPath(unreachable),
                "With every prefix unreachable, getPath's trial probes must all fail fast so no "
                + "file system is resolved and FileSystemNotFoundException is thrown. If the "
                + "bare-root probe instead inherits MEMORY_AND_DISK/OFFLINE from the JVM-global "
                + "default (the 1.1.10 regression), it goes offline and getPath returns a Path "
                + "bound to the wrong root -- the .seq resolution bug.");
    }
}
