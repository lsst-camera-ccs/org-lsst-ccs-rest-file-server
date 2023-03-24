package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;
import org.lsst.ccs.rest.file.server.client.implementation.Cache.CacheEntry;

/**
 *
 * @author tonyj
 */
class CacheRequestFilter implements ClientRequestFilter {

    private final Cache cache;
    private final RestFileSystemOptions.CacheFallback cacheOption;

    CacheRequestFilter(Cache cache, RestFileSystemOptions.CacheFallback cacheOption) {
        this.cache = cache;
        this.cacheOption = cacheOption;
    }

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        if (!ctx.getMethod().equalsIgnoreCase("GET")) {
            if (cacheOption == RestFileSystemOptions.CacheFallback.ALWAYS) {
                throw new OfflineException("Illegal method " + ctx.getMethod() + " for offline cache");
            }
            return;
        }

        CacheEntry entry = cache.getEntry(ctx.getUri());
        if (entry == null) {
            if (cacheOption == RestFileSystemOptions.CacheFallback.ALWAYS) {
                throw new OfflineException("Read of non-cached item in offline mode " + ctx.getUri());
            }
            return;
        }

        if (!entry.isExpired() || cacheOption.compareTo(RestFileSystemOptions.CacheFallback.WHEN_POSSIBLE) >=0 ) {
            ByteArrayInputStream is = new ByteArrayInputStream(entry.getContent());
            Response response = Response.ok(is).type(entry.getContentType()).build();
            ctx.abortWith(response);
            return;
        }

        String etag = entry.getETagHeader();
        Date lastModified = entry.getLastModified();

        if (etag != null) {
            ctx.getHeaders().putSingle("If-None-Match", etag);
        }

        if (lastModified != null) {
            ctx.getHeaders().putSingle("If-Modified-Since", lastModified);
        }
    }
    
    static class OfflineException extends IOException {

        private OfflineException(String message) {
            super(message);
        }
    }
}
