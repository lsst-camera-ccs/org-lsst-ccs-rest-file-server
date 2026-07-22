package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.client.ClientResponseContext;
import org.apache.commons.jcs3.JCS;
import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.auxiliary.disk.indexed.IndexedDiskCache;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.jcs3.log.LogManager;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * Simple in-memory or disk-backed cache used to store server responses. The
 * cache is optional and its behaviour is controlled by
 * {@link RestFileSystemOptions}.
 * <p>
 * There is one cache per JVM (see ADR 0003): a single JCS {@code default}
 * region and, for {@code MEMORY_AND_DISK}, a single disk store at the resolved
 * global cache location, shared by every mount. {@code Cache} is policy-free
 * storage; the freshness/expiry policy lives in the per-mount
 * {@link CacheRequestFilter}.
 */
class Cache implements Closeable {

    private CacheAccess<URI, CacheEntry> map;
    private FileLock lock;
    private Path diskCacheLocation;

    /**
     * Creates a new cache instance based on the supplied options.
     *
     * @param options user supplied configuration
     * @throws IOException if the cache cannot be initialised
     */
    Cache(RestFileSystemOptionsHelper options) throws IOException {

        JCS.setLogSystem(LogManager.LOGSYSTEM_JAVA_UTIL_LOGGING);

        if (!options.isCacheLogging()) {
            Logger logger = Logger.getLogger("org.apache.commons.jcs3");
            logger.setLevel(Level.WARNING);
        }

        Properties props = new Properties();
        try ( InputStream in = Cache.class.getResourceAsStream("memory.ccf")) {
            props.load(in);
        }
        if (options.getCacheOptions() == RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK) {
            try ( InputStream in = Cache.class.getResourceAsStream("disk.ccf")) {
                props.load(in);
            }
            Path cacheLocation = lockCacheLocation();
            this.diskCacheLocation = cacheLocation;
            props.setProperty("jcs.auxiliary.DC.attributes.DiskPath", cacheLocation.toAbsolutePath().toString());
        }
        CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
        ccm.configure(props);
        map = JCS.getInstance("default");
    }

    /**
     * Acquires the disk-cache location for this JVM. The lock on
     * {@code <loc>/lockFile} has three outcomes, distinguished by the fact that
     * a Java {@link FileLock} is JVM-scoped:
     * <ul>
     *   <li>{@code tryLock()} returns a lock &rarr; nobody holds it; this mount
     *       owns the location and keeps the lock.</li>
     *   <li>{@link OverlappingFileLockException} &rarr; another mount in
     *       <em>this</em> JVM already holds it (a foreign process would instead
     *       yield {@code null}); share the location, taking no lock.</li>
     *   <li>{@code tryLock()} returns {@code null} &rarr; another <em>process</em>
     *       holds it; spill to {@code <loc>-N} if allowed, else fail.</li>
     * </ul>
     * The lock is only a cross-JVM guard. Note: it is held by whichever mount
     * acquires it first and released when <em>that</em> mount closes, not when
     * the last sharer closes.
     *
     * @return the resolved disk-cache location
     * @throws IOException if no usable location can be locked
     */
    private Path lockCacheLocation() throws IOException {
        Path base = RestFileSystemOptionsHelper.getGlobalCacheLocation();
        boolean allowAlternate = RestFileSystemOptionsHelper.allowAlternateCacheLocation();
        Path cacheLocation = base;
        for (int n = 1; n < 100; n++) {
            Files.createDirectories(cacheLocation);
            if (!(Files.isDirectory(cacheLocation) || Files.isWritable(cacheLocation))) {
                throw new IOException("Invalid cache location: " + cacheLocation);
            }
            Path lockFile = cacheLocation.resolve("lockFile");
            FileChannel lockFileChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try {
                lock = lockFileChannel.tryLock();
                if (lock != null) {
                    return cacheLocation;
                }
                // Another process holds the lock on this location.
                lockFileChannel.close();
                if (!allowAlternate) {
                    throw new IOException("Cache already in use: " + cacheLocation);
                }
                // Spill to a flat sibling of the base (<base>-1, <base>-2, …); suffix
                // the pristine base, not the already-suffixed candidate, or the names
                // compound (<base>-1-2-3).
                cacheLocation = base.resolveSibling(base.getFileName() + "-" + n);
            } catch (OverlappingFileLockException x) {
                // Another mount in this JVM already holds the lock; share the location.
                lockFileChannel.close();
                return cacheLocation;
            }
        }
        throw new IOException("Cache already in use and unable to get alternate cache location");
    }

    /**
     * Returns the resolved disk cache directory, or {@code null} when no disk
     * cache is in use.
     *
     * @return the disk cache directory, or {@code null}
     */
    Path getDiskCacheLocation() {
        return diskCacheLocation;
    }

    CacheEntry getEntry(URI uri) {
        return map.get(uri);
    }

    void cacheResponse(ClientResponseContext response, URI uri) throws IOException {
        map.put(uri, new CacheEntry(response));
    }

    @Override
    public void close() throws IOException {
        // This is super ugly, but otherwise we always get a SEVERE error because
        // the cache manager appears to shutdown the auxilliary disk cache once when it shuts
        // down the memory cache, and then again when it shuts down the auxilliary caches. This
        // looks like a bug, so here we turn off logging to avoid confusing messsages.

        Logger logger = Logger.getLogger(IndexedDiskCache.class.getName());
        logger.setLevel(Level.OFF);

        if (lock != null) {
            lock.close();
            lock.channel().close();
            lock = null;
        }

    }

    /**
     * Serializable representation of a cached HTTP response.
     */
    public static class CacheEntry implements Serializable {

        private String tag;
        private Date lastModified;
        private String mediaType;
        private byte[] bytes;
        private volatile int updateCount = 0;

        static final long serialVersionUID = 1521062449875932852L;

        /**
         * Creates an empty cache entry. Used only for serialization.
         */
        public CacheEntry() {

        }

        private CacheEntry(ClientResponseContext response) throws IOException {
            tag = response.getEntityTag() == null ? null : response.getEntityTag().toString();
            lastModified = response.getLastModified();
            mediaType = response.getMediaType().toString();
            InputStream in = response.getEntityStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8096];
            for (;;) {
                int l = in.read(buffer);
                if (l < 0) {
                    break;
                }
                out.write(buffer, 0, l);
            }
            out.close();
            bytes = out.toByteArray();
            response.setEntityStream(new ByteArrayInputStream(bytes));
        }

        byte[] getContent() {
            return bytes;
        }

        String getContentType() {
            return mediaType;
        }

        String getETagHeader() {
            return tag;
        }

        Date getLastModified() {
            return lastModified;
        }

        /**
         * Called when the cache entry has been checked, and found to be up-to-date.
         * @param response The server response, used to extract the eTag and lastModified date.
         */
        void updateCacheHeaders(ClientResponseContext response) {
            tag = response.getEntityTag() == null ? null : response.getEntityTag().toString();
            lastModified = response.getLastModified();
            updateCount++;
        }

        int getUpdateCount() {
            return updateCount;
        }
    }
}
