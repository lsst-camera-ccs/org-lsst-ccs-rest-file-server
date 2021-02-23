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
 *
 * @author tonyj
 */
public class Main {

    private final URI serverURI;
    private final HttpServer httpServer;

    public static void main(String[] args) throws IOException, URISyntaxException {
        Main main = new Main();
        main.run();
    }

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

    private void run() {
        httpServer.start();
        System.out.println("Config file server running at " + serverURI);
    }
}
