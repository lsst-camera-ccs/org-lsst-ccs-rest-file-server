package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned when the REST file server encounters an
 * {@link java.io.IOException}.
 *
 * @author tonyj
 */
public class IOExceptionResponse {

    public final static int RESPONSE_CODE = 406;
    private final String exceptionClass;
    private final String message;

    /**
     * Creates a new response describing the encountered exception.
     *
     * @param exceptionClass fully qualified class name of the exception
     * @param message error message from the exception
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IOExceptionResponse(@JsonProperty("exceptionClass") String exceptionClass, @JsonProperty("message") String message) {
        this.exceptionClass = exceptionClass;
        this.message = message;
    }

    /**
     * Gets the fully qualified class name of the exception.
     *
     * @return exception class name
     */
    public String getExceptionClass() {
        return exceptionClass;
    }

    /**
     * Gets the exception message.
     *
     * @return exception message
     */
    public String getMessage() {
        return message;
    }

}
