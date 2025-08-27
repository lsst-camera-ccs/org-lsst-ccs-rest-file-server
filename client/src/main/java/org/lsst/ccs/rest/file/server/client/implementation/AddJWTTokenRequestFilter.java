package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;

/**
 * {@link ClientRequestFilter} that adds a JSON Web Token (JWT) to each request
 * so that the REST server can authenticate the client.
 */
public class AddJWTTokenRequestFilter implements ClientRequestFilter {
    private static final String JWT_HEADER_KEY = HttpHeaders.AUTHORIZATION;
    private final String jwt;

    /**
     * Creates a new filter that attaches the supplied token as a bearer
     * authorization header.
     *
     * @param jwt the JWT to include; may be {@code null} if no token is
     *            available
     */
    AddJWTTokenRequestFilter(String jwt) {
        this.jwt = jwt;
    }
    
    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (jwt != null) requestContext.getHeaders().add(JWT_HEADER_KEY, "Bearer "+jwt);
    }    
}
