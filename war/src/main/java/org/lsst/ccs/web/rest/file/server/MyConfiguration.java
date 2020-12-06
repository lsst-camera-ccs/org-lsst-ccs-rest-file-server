package org.lsst.ccs.web.rest.file.server;


import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 *
 * @author tonyj
 */
@ApplicationPath("rest")
public final class MyConfiguration extends ResourceConfig {

    public MyConfiguration() {
        register(JacksonFeature.class);
        register(CORSResponseFilter.class);
        register(FileServer.class);
        register(VersionedFileServer.class);
        register(IOExceptionMapper.class);
    }
}