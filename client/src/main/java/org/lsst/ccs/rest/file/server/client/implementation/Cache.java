package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Path;
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
 * <p>
 * The disk location and its cross-JVM lock are resolved once per JVM by
 * {@link RestFileSystemOptionsHelper#lockCacheLocation()} (see ADR 0004);
 * {@code Cache} only reads the result and never touches the {@code lockFile}
 * itself.
 */
class Cache implements Closeable {

    private CacheAccess<URI, CacheEntry> map;
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
            // Location + cross-JVM lock are JVM-global and acquired once (ADR 0004).
            Path cacheLocation = RestFileSystemOptionsHelper.lockCacheLocation();
            this.diskCacheLocation = cacheLocation;
            props.setProperty("jcs.auxiliary.DC.attributes.DiskPath", cacheLocation.toAbsolutePath().toString());
        }
        CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
        ccm.configure(props);
        map = JCS.getInstance("default");
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

        // The disk-cache lock is JVM-global (ADR 0004) and deliberately not
        // released here: it is held for the JVM lifetime and reclaimed by the OS
        // on process exit. Releasing per mount is exactly the defect 0004 fixed.
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
