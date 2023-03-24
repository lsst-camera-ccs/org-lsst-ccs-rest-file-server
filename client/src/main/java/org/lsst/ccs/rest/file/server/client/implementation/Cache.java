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
 *
 * @author tonyj
 */
class Cache implements Closeable {

    private CacheAccess<URI, CacheEntry> map;
    private FileLock lock;

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
            Path cacheLocation = options.getDiskCacheLocation();
            if (cacheLocation != null) {
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

                props.setProperty("jcs.auxiliary.DC.attributes.DiskPath", cacheLocation.toAbsolutePath().toString());
            }
        }
        CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
        ccm.configure(props);
        map = JCS.getInstance("default");
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

    public static class CacheEntry implements Serializable {

        private String tag;
        private Date lastModified;
        private String mediaType;
        private byte[] bytes;
        private transient long lastUpdated;

        static final long serialVersionUID = 1521062449875932852L;

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
            lastUpdated = System.currentTimeMillis();
        }

        boolean isExpired() {
            return true;
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

        void updateCacheHeaders(ClientResponseContext response) {
            lastUpdated = System.currentTimeMillis();
            tag = response.getEntityTag() == null ? null : response.getEntityTag().toString();
            lastModified = response.getLastModified();
        }
        
        long getLastUpdated() {
            return lastUpdated;
        }

    }
}
