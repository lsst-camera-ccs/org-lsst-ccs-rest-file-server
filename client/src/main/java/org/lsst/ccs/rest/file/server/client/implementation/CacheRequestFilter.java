package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import org.lsst.ccs.rest.file.server.client.implementation.Cache.CacheEntry;

/**
 *
 * @author tonyj
 */
public class CacheRequestFilter implements ClientRequestFilter {
   private final Cache cache;

   public CacheRequestFilter(Cache cache) {
      this.cache = cache;
   }

   @Override
   public void filter(ClientRequestContext ctx) throws IOException {
      if (!ctx.getMethod().equalsIgnoreCase("GET")) return;

      CacheEntry entry = cache.getEntry(ctx.getUri());
      if (entry == null) return;

      if (!entry.isExpired()) {
         ByteArrayInputStream is = new ByteArrayInputStream(entry.getContent());
         Response response = Response.ok(is).type(entry.getContentType()).build();
         ctx.abortWith(response);
         return;
      }

      EntityTag etag = entry.getETagHeader();
      Date lastModified = entry.getLastModified();

      if (etag != null) {
         ctx.getHeaders().putSingle("If-None-Match", etag);
      }

      if (lastModified != null) {
         ctx.getHeaders().putSingle("If-Modified-Since", lastModified);
      }
   }
}