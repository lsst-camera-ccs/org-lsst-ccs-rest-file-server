package org.lsst.ccs.web.rest.file.server.standalone;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.lsst.ccs.web.rest.file.server.MyConfiguration;

/**
 * Standalone launcher for the REST file server using the JDK HTTP server.
 * It bootstraps {@link MyConfiguration} and exposes the application on a
 * local port for development or testing purposes.
 *
 * @author tonyj
 */
public class Main {

    private final URI serverURI;
    private final HttpServer httpServer;

    /**
     * Starts the server from the command line.
     *
     * @param args command line arguments (ignored)
     * @throws IOException if the server cannot be started
     * @throws URISyntaxException if the server URI is invalid
     */
    public static void main(String[] args) throws IOException, URISyntaxException {
        Main main = new Main();
        main.run();
    }

    /**
     * Creates a new instance configured to serve files from a temporary
     * directory on a fixed local port.
     *
     * @throws IOException if the file system cannot be prepared
     * @throws URISyntaxException if the base URI is invalid
     */
    Main() throws IOException, URISyntaxException {
        Path root = Paths.get("configFiles");
        Files.createDirectory(root);
        int port = 8899;
        serverURI = new URI("http://localhost:" + port + "/");
        MyConfiguration rc = new MyConfiguration();
        rc.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(root).to(Path.class);
            }
        });
        httpServer = JdkHttpServerFactory.createHttpServer(serverURI.resolve("rest"), rc, false);
    }

    /**
     * Starts the underlying HTTP server.
     */
    private void run() {
        httpServer.start();
        System.out.println("Config file server running at " + serverURI);
    }
}
