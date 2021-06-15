package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import static org.lsst.ccs.web.rest.file.server.data.Constants.PROTOCOL_VERSION_HEADER;

/**
 *
 * @author tonyj
 */
public class AddProtcolVersionRequestFilter implements ClientRequestFilter {
    public static final String FILTER_HEADER_VALUE = "2";
    public static final String FILTER_HEADER_KEY = PROTOCOL_VERSION_HEADER;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        requestContext.getHeaders().add(FILTER_HEADER_KEY, FILTER_HEADER_VALUE);
    }    
}
