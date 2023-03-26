package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import org.lsst.ccs.rest.file.server.client.implementation.Cache.CacheEntry;

/**
 * A filter applied to responses from the server. Fetches the data from the cache
 * if the server responds that it is up-to-date. The cache is used for all responses
 * from the server, including directory listings and file info.
 * @author tonyj
 */
class CacheResponseFilter implements ClientResponseFilter {

    private final Cache cache;

    CacheResponseFilter(Cache cache) {
        this.cache = cache;
    }

    @Override
    public void filter(ClientRequestContext request,
            ClientResponseContext response)
            throws IOException {
        if (!request.getMethod().equalsIgnoreCase("GET")) {
            return;
        }

        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            cache.cacheResponse(response, request.getUri());
        } else if (response.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode()) {
            // Use the cache
            CacheEntry entry = cache.getEntry(request.getUri());
            entry.updateCacheHeaders(response);
            response.getHeaders().clear();
            response.setStatus(Response.Status.OK.getStatusCode());
            response.getHeaders().putSingle("Content-Type", entry.getContentType());
            ByteArrayInputStream is = new ByteArrayInputStream(entry.getContent());
            response.setEntityStream(is);
        }
    }
}
