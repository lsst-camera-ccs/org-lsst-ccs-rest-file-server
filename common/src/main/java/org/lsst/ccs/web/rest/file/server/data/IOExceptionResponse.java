package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A response sent when a IOException is generated in the rest file server.
 * @author tonyj
 */
public class IOExceptionResponse {

    public final static int RESPONSE_CODE = 406;
    private final String exceptionClass;
    private final String message;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IOExceptionResponse(@JsonProperty("exceptionClass") String exceptionClass, @JsonProperty("message") String message) {
        this.exceptionClass = exceptionClass;
        this.message = message;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getMessage() {
        return message;
    }

}
