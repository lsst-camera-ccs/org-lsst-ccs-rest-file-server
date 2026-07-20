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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 */
class Cache implements Closeable {

    private CacheAccess<URI, CacheEntry> map;
    private FileLock lock;
    private RestFileSystemOptions.CacheFallback cacheFallback;
    private final String region;
    private Path diskCacheLocation;

    /**
     * Creates a new cache instance based on the supplied options.
     * <p>
     * Each remote file system gets its own JCS region and (for
     * {@code MEMORY_AND_DISK}) its own disk directory, both derived from a
     * stable key computed from {@code serverURI}. This keeps the caches of
     * multiple file systems in the same JVM fully isolated, so they neither
     * share a region nor collide on the disk lock.
     *
     * @param options user supplied configuration
     * @param serverURI the resolved full URI of the server (server root plus
     *                   mount point) used to derive the per-server cache key
     * @throws IOException if the cache cannot be initialised
     */
    Cache(RestFileSystemOptionsHelper options, URI serverURI) throws IOException {

        this.region = regionKey(serverURI);

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
            String aux = "DC_" + region;
            Properties diskProps = new Properties();
            try ( InputStream in = Cache.class.getResourceAsStream("disk.ccf")) {
                diskProps.load(in);
            }
            // Substitute the per-server region and auxiliary names into the disk template.
            for (String name : diskProps.stringPropertyNames()) {
                String key = name.replace("%REGION%", region).replace("%AUX%", aux);
                String value = diskProps.getProperty(name).replace("%REGION%", region).replace("%AUX%", aux);
                props.setProperty(key, value);
            }
            Path baseLocation = options.getDiskCacheLocation();
            if (baseLocation != null) {
                // Each server caches under its own subdirectory of the configured base
                // location, so distinct servers never contend for the same disk lock.
                Path cacheLocation = baseLocation.resolve(region);
                // Check that we can lock the cache, and if not what to do about it
                for (int n = 1; n < 100; n++) {
                    Files.createDirectories(cacheLocation);
                    if (Files.isDirectory(cacheLocation) || Files.isWritable(cacheLocation)) {
                        Path lockFile = cacheLocation.resolve("lockFile");
                        FileChannel lockFileChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        try {
                            lock = lockFileChannel.tryLock();
                            if (lock != null) {
                                break;
                            } else if (!options.allowAlternateCacheLoction()) {
                                throw new IOException("Cache already in use");
                            }
                        } catch (OverlappingFileLockException x) {
                            if (!options.allowAlternateCacheLoction()) {
                                throw new IOException("Cache already in use", x);
                            }
                        }
                        cacheLocation = cacheLocation.resolveSibling(cacheLocation.getFileName() + "-" + n);

                    } else {
                        throw new IOException("Invalid cache location: " + cacheLocation);
                    }
                }
                if (lock == null) {
                    throw new IOException("Cache already in use and unable to get alternate cache location");
                }

                this.diskCacheLocation = cacheLocation;
                props.setProperty("jcs.auxiliary." + aux + ".attributes.DiskPath", cacheLocation.toAbsolutePath().toString());
            }
        }
        CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
        ccm.configure(props);
        map = JCS.getInstance(region);
        this.cacheFallback = options.getCacheFallback();
    }

    /**
     * Derives a stable, filesystem- and JCS-safe key from the server URI. The
     * key is deterministic across restarts so a server reattaches to its own
     * on-disk cache. A short hash of the full URI is appended so that URIs that
     * sanitise to the same token do not collide.
     *
     * @param serverURI the resolved full server URI
     * @return the per-server cache key
     */
    private static String regionKey(URI serverURI) {
        String raw = serverURI.toString();
        String sanitized = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(sanitized.length() - 64);
        }
        return sanitized + "_" + shortHash(raw);
    }

    private static String shortHash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException x) {
            // SHA-256 is guaranteed to be available on every JVM.
            throw new IllegalStateException(x);
        }
    }

    /**
     * Returns the JCS region name used by this cache. Distinct remote file
     * systems have distinct regions.
     *
     * @return the per-server region name
     */
    String getRegion() {
        return region;
    }

    /**
     * Returns the resolved per-server disk cache directory, or {@code null}
     * when no disk cache is in use.
     *
     * @return the disk cache directory, or {@code null}
     */
    Path getDiskCacheLocation() {
        return diskCacheLocation;
    }

    void setCacheFallbackOption(RestFileSystemOptions.CacheFallback cacheFallback) {
        this.cacheFallback = cacheFallback;
    }
    
    boolean doEntriesExpire() {
        return cacheFallback != RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE;
    }
    
    CacheEntry getEntry(URI uri) {
        CacheEntry e = map.get(uri);
        if ( e != null ) {
            e.setIsExpired(doEntriesExpire());
        }
        return e;
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
        private boolean isExpired = true;

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

        /**
         * Expired indicates that the cache entry should be checked for freshness before
         * use. The current implementation always returns true, meaning we always go back to
         * the server (if it is online) to check that the cache entry is up-to-date. It would
         * be possible to return false if the cache was recently created/updated, to reduce the need
         * to constantly check the freshness of the cache if the same data is read repeatedly.
         * @return <code>true</code> if the cache entry should be checked.
         */
        boolean isExpired() {
            return isExpired;
        }
        
        void setIsExpired(boolean isExpired) {
            this.isExpired = isExpired;
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
