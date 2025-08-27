package org.lsst.ccs.web.rest.file.server;

import java.io.IOException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.lsst.ccs.web.rest.file.server.data.IOExceptionResponse;

/**
 * Maps an {@link IOException} thrown by the server to a serialized
 * {@link IOExceptionResponse} so the client receives structured error
 * information.
 *
 * @author tonyj
 */
class IOExceptionMapper implements ExceptionMapper<IOException> {

    /**
     * Converts the supplied {@link IOException} into an HTTP response with a
     * JSON body describing the error.
     *
     * @param e the exception to map
     * @return a response containing an {@link IOExceptionResponse}
     */
    @Override
    public Response toResponse(IOException e) {
        return Response
                .status(IOExceptionResponse.RESPONSE_CODE)
                .type(MediaType.APPLICATION_JSON)
                .entity(new IOExceptionResponse(e.getClass().getCanonicalName(), e.getMessage()))
                .build();
    }
    
}
