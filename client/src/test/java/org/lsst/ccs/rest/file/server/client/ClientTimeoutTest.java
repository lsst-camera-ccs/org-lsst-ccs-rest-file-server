package org.lsst.ccs.rest.file.server.client;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 *
 * @author The LSST CCS Team
 */
public class ClientTimeoutTest {

    private final static int port = 9998;
    private final static String host = "http://localhost/";
    
    private static HttpServer httpServer;
    
    
    public ClientTimeoutTest() {
    }

    @BeforeAll
    public static void setUpClass() throws URISyntaxException, IOException {
        URI baseUri = UriBuilder.fromUri(host).port(port).build();
        ResourceConfig config = new ResourceConfig(WelcomeMessage.class,WaitForever.class);
        httpServer = JdkHttpServerFactory.createHttpServer(baseUri, config, true);
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        httpServer.stop(0);
    }

    
    @Test
    public void testServerTimeout() throws Exception {        
        Client client = ClientBuilder.newBuilder().readTimeout(15, TimeUnit.SECONDS).build();
        URI serverURI = new URI("http://localhost:"+port+"/");
        //Simulate timeout from the server
        URI welcomeRestURI = UriBuilder.fromUri(serverURI).path("waitforever").queryParam("maxwait", 1000L).build();

        WebTarget target = client.target(welcomeRestURI);
        Response r = target.request().get();
        
        Assert.assertTrue(r.getStatus() == 500);
        
    }
    
    @Test
    public void testReadTimeout() throws Exception {        
        Client client = ClientBuilder.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
        URI serverURI = new URI("http://localhost:"+port+"/");
        //Simulate timeout from the server
        URI welcomeRestURI = UriBuilder.fromUri(serverURI).path("waitforever").queryParam("maxwait", 5000L).build();

        WebTarget target = client.target(welcomeRestURI);
        try {
            Response r = target.request().get();
            Assert.assertTrue(false);
        } catch (Exception e ) {
            Assert.assertTrue(e.getCause() instanceof SocketTimeoutException);
        }
        
    }
    
    
    @Path("/waitforever")
    public static class WaitForever {

        private final long wait = 1000L;

        @GET
        public String waitForever(@QueryParam("maxwait") int maxwait) {
            long overallWait = 0;
            while(true) {
                if ( overallWait >= maxwait ) {
                    throw new RuntimeException("Waited too long");
                }
                try {
                    Thread.sleep(wait);
                    overallWait += wait;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
    
    
    
    @Path("/welcome")
    public static class WelcomeMessage {

        private String welcomeMessage = "Hello world!";

        @GET
        public String returnWelcomeMessage() {
            return welcomeMessage;
        }

        @PUT
        public String updateWelcomeMessage(String aNewMessage) {
            welcomeMessage = aNewMessage;
            return "Welcome message updated";
        }
    }
}
