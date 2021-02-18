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
        try (InputStream in = Cache.class.getResourceAsStream("memory.ccf")) {
            props.load(in);
        }
        if (options.getCacheOptions() == RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK) {
            try (InputStream in = Cache.class.getResourceAsStream("disk.ccf")) {
                props.load(in);
            }
            Path cacheLocation = options.getDiskCacheLocation();
            if (cacheLocation != null) {

                // Check that we can lock the cache, and if not what to do about it
                Files.createDirectories(cacheLocation);
                if (Files.isDirectory(cacheLocation) || Files.isWritable(cacheLocation)) {
                    Path lockFile = cacheLocation.resolve("lockFile");
                    for (int n=0; n<10; n++) {
                        FileChannel lockFileChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        try {
                            lock = lockFileChannel.lock();
                            break;
                        } catch (OverlappingFileLockException x) {
                            if (!options.allowAlternateCacheLoction()) {
                                throw new IOException("Cache already in use", x);
                            } else {
                                lockFile = cacheLocation.resolve("lockFile-"+n);
                            }
                        }
                    }
                } else {
                    throw new IOException("Invalid cache location: " + cacheLocation);
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
        lock.close();
        lock.channel().close();
    }

    public static class CacheEntry implements Serializable {

        private String tag;
        private Date lastModified;
        private String mediaType;
        private byte[] bytes;

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
            tag = response.getEntityTag() == null ? null : response.getEntityTag().toString();
            lastModified = response.getLastModified();
        }

    }
}
