package org.lsst.ccs.web.rest.file.server;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

/**
 * {@link ContainerResponseFilter} that adds standard Cross-Origin Resource
 * Sharing (CORS) headers to every HTTP response.
 */
@Provider
public class CORSResponseFilter implements ContainerResponseFilter {

    /**
     * Adds permissive CORS headers to the given response.
     *
     * @param requestContext the incoming request
     * @param responseContext the outgoing response to augment
     * @throws IOException if an I/O error occurs while filtering
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {

        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "POST, PUT, GET, DELETE");
        headers.add("Access-Control-Allow-Headers", "*");

    }
}
