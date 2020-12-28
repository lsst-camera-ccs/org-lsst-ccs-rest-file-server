package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import org.lsst.ccs.rest.file.server.client.implementation.Cache.CacheEntry;

/**
 *
 * @author tonyj
 */
public class CacheResponseFilter implements ClientResponseFilter {

    private final Cache cache;

    public CacheResponseFilter(Cache cache) {
        this.cache = cache;
    }

    @Override
    public void filter(ClientRequestContext request,
            ClientResponseContext response)
            throws IOException {
        if (!request.getMethod().equalsIgnoreCase("GET")) {
            return;
        }

        if (response.getStatus() == 200) {
            cache.cacheResponse(response, request.getUri());
        } else if (response.getStatus() == 304) {
            System.out.println("Got 304 for " + request.getUri());
            CacheEntry entry = cache.getEntry(request.getUri());
            entry.updateCacheHeaders(response);
            response.getHeaders().clear();
            response.setStatus(200);
            response.getHeaders().putSingle("Content-Type", entry.getContentType().toString());
            ByteArrayInputStream is = new ByteArrayInputStream(entry.getContent());
            response.setEntityStream(is);
        }
    }
}
