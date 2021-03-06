package org.lsst.ccs.web.rest.file.server;

import java.io.IOException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.lsst.ccs.web.rest.file.server.data.IOExceptionResponse;

/**
 * Maps an IOException thrown in the server to a IOExceptionResponse
 * @author tonyj
 */
class IOExceptionMapper implements ExceptionMapper<IOException> {

    @Override
    public Response toResponse(IOException e) {
        return Response
                .status(IOExceptionResponse.RESPONSE_CODE)
                .type(MediaType.APPLICATION_JSON)
                .entity(new IOExceptionResponse(e.getClass().getCanonicalName(),e.getMessage()))
                .build();
    }
    
}
