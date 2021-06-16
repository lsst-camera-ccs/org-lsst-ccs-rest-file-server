package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;

/**
 *
 * @author tonyj
 */
public class AddJWTTokenRequestFilter implements ClientRequestFilter {
    private static final String JWT_HEADER_KEY = HttpHeaders.AUTHORIZATION;
    private final String jwt;

    AddJWTTokenRequestFilter(String jwt) {
        this.jwt = jwt;
    }
    
    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (jwt != null) requestContext.getHeaders().add(JWT_HEADER_KEY, "Bearer "+jwt);
    }    
}
