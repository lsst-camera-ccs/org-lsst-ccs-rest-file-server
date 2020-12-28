package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author tonyj
 */
public class Cache {

    ConcurrentHashMap<URI, CacheEntry> map = new ConcurrentHashMap<>();

    CacheEntry getEntry(URI uri) {
        return map.get(uri);
    }

    void cacheResponse(ClientResponseContext response, URI uri) throws IOException {
        map.put(uri, new CacheEntry(response));
    }

    public static class CacheEntry {

        private EntityTag tag;
        private Date lastModified;
        private final MediaType mediaType;
        private final byte[] bytes;

        private CacheEntry(ClientResponseContext response) throws IOException {
            tag = response.getEntityTag();
            lastModified = response.getLastModified();
            mediaType = response.getMediaType();
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

        MediaType getContentType() {
            return mediaType;
        }

        EntityTag getETagHeader() {
            return tag == null ? null : tag;
        }

        Date getLastModified() {
            return lastModified == null ? null : lastModified;
        }

        void updateCacheHeaders(ClientResponseContext response) {
            tag = response.getEntityTag();
            lastModified = response.getLastModified();
        }

    }
}
