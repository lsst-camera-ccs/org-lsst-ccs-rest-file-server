package org.lsst.ccs.web.rest.file.server;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;

/**
 *
 * @author tonyj
 */
public class TestServer {

    private Path tempDir;
    private final URI serverURI;
    private final HttpServer httpServer;

    public TestServer() throws URISyntaxException, IOException {
        tempDir = Files.createTempDirectory("RestServer");
        MyConfiguration rc = new MyConfiguration();
        rc.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(tempDir).to(Path.class);
            }
        });
        serverURI = new URI("http://localhost:9999/");
        httpServer = JdkHttpServerFactory.createHttpServer(serverURI.resolve("rest"), rc, true);
    }

    /**
     * Deletes all of the files in the tempDir, but not the tempDir itself
     * @throws IOException 
     */
    public final void cleanFiles() throws IOException {
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).filter(p -> p != tempDir).map(Path::toFile).forEach(File::delete);
    }

    public void shutdown() throws IOException {
        httpServer.stop(0);
        cleanFiles();
        Files.delete(tempDir);
    }

    public URI getServerURI() {
        return serverURI;
    }

}
