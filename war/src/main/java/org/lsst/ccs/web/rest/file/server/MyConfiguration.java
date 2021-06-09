package org.lsst.ccs.web.rest.file.server;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.IOException;
import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.lsst.ccs.web.rest.file.server.jwt.JWTTokenNeededFilter;

/**
 *
 * @author tonyj
 */
@ApplicationPath("rest")
public final class MyConfiguration extends ResourceConfig {

    public MyConfiguration() throws IOException {
        this(false);
    }

    public MyConfiguration(boolean skipAuthentication) throws IOException {

        if (!skipAuthentication) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();

            FirebaseApp.initializeApp(options);
            register(JWTTokenNeededFilter.class);
        }

        register(JacksonFeature.class);
        register(CORSResponseFilter.class);
        register(FileServer.class);
        register(VersionedFileServer.class);
        register(IOExceptionMapper.class);
    }
}
