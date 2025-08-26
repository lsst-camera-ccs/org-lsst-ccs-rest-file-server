package org.lsst.ccs.web.rest.file.server.jwt;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * {@link ContainerRequestFilter} that enforces JWT authentication for
 * resources annotated with {@link JWTTokenNeeded}. The filter optionally
 * restricts access to a set of allowed IP addresses supplied via the
 * {@code CCS_REST_ALLOWED_IPS} environment variable.
 *
 * @author tonyj
 */
@Provider
@JWTTokenNeeded
@Priority(Priorities.AUTHENTICATION)
public class JWTTokenNeededFilter implements ContainerRequestFilter {
    
    @Context
    private HttpServletRequest httpServletRequest;
    
    private static final String allowedIPs = System.getenv("CCS_REST_ALLOWED_IPS");
    private static final Pattern ALLOWED_IPS_PATTERN = allowedIPs == null ? null : Pattern.compile(allowedIPs);

    /**
     * Validates the JWT token and optional client IP address present in the
     * current request. If validation fails the request is aborted with
     * {@link Response.Status#UNAUTHORIZED}.
     *
     * @param requestContext context for the incoming request
     * @throws IOException if an error occurs during verification
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        
        if (ALLOWED_IPS_PATTERN != null) {
            String remoteIp = httpServletRequest.getRemoteAddr();
            String forwardedFor = httpServletRequest.getHeader("X-FORWARDED-FOR");
            if ( forwardedFor != null ) {
                String[] ipChain = forwardedFor.split(",");
                if ( ipChain.length > 0 ) {
                    remoteIp = ipChain[0].trim();
                }
            }
            if ( ALLOWED_IPS_PATTERN.matcher(remoteIp).matches() ) {
                return;
            } else {
                LOG.log(Level.INFO, "Remote IP {0} could not be allowed against {1}", new Object[] {remoteIp,allowedIPs});
            }
        }

        // Get the HTTP Authorization header from the request
        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        } else {
            // Extract the token from the HTTP Authorization header
            String token = authorizationHeader.substring("Bearer".length()).trim();

            try {
                // Validate the token
                System.out.println("Got token " + token);
                System.out.println(" from "+httpServletRequest.getRemoteAddr());
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
                String uid = decodedToken.getUid();
                System.out.println("Got uid " + uid);
            } catch (FirebaseAuthException e) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            }
        }
    }
    private static final Logger LOG = Logger.getLogger(JWTTokenNeededFilter.class.getName());
}
