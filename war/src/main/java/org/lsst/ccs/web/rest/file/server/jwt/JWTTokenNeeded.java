package org.lsst.ccs.web.rest.file.server.jwt;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Name-binding annotation used to mark JAX-RS resource classes or methods
 * that require JWT-based authentication.
 *
 * @author tonyj
 */
@javax.ws.rs.NameBinding
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface JWTTokenNeeded {
}
