package org.lsst.ccs.web.rest.file.server;

import org.lsst.ccs.web.rest.file.server.data.RestFileInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author tonyj
 */
public class FileServerTest {

    private static TestServer testServer;

    public static URI getURI() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public FileServerTest() {
    }

    @BeforeAll
    public static void setUpClass() throws URISyntaxException, IOException {
         testServer = new TestServer();
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        testServer.shutdown();
    }

    @BeforeEach
    public void setUp() throws URISyntaxException {

    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testBasicFileOperations() throws URISyntaxException, InterruptedException, ProtocolException, MalformedURLException, IOException {

        final Client client = ClientBuilder.newClient();
        try {
            RestFileInfo fileInfo = list(client);
            assertEquals(0, fileInfo.getChildren().size());

            final String testFile = "test.file";
            final String content = "Test content";
            upload(testFile, content);

            RestFileInfo fileInfo2 = list(client);
            assertEquals(1, fileInfo2.getChildren().size());

            RestFileInfo fileInfo4 = info(client, testFile);
            assertNull(fileInfo4.getChildren());
            assertEquals(testFile, fileInfo4.getName());
            assertEquals(content.length(), fileInfo4.getSize());
            //assertEquals("text/plain", fileInfo4.getMimeType());
            
            download(testFile, content);

            delete(client, testFile);
            
            RestFileInfo fileInfo3 = list(client);
            assertEquals(0, fileInfo3.getChildren().size());
        } finally {
            client.close();
        }
    }
    
    private void download(final String testFile, final String content) throws IOException {
        // Get the file back
        URI downloadURI = testServer.getServerURI().resolve("rest/download/" + testFile);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(downloadURI.toURL().openStream()))) {
            String line = reader.readLine();
            assertEquals(content, line);
        }
    }

    private void upload(final String testFile, final String content) throws IOException, ProtocolException {
        // upload a file
        URI uploadURI = testServer.getServerURI().resolve("rest/upload/" + testFile);
        HttpURLConnection connection = (HttpURLConnection) uploadURI.toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestMethod("POST");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(content.getBytes());
        }
        assertEquals(200, connection.getResponseCode());
    }

    private void delete(Client client, String file) {
        // Delete the file
        URI deleteURI = testServer.getServerURI().resolve("rest/deleteFile/" + file);
        Response response3 = client.target(deleteURI).request(MediaType.APPLICATION_JSON).delete();
        assertEquals(200, response3.getStatus());
    }

    private RestFileInfo list(Client client) {
        URI listURI = testServer.getServerURI().resolve("rest/list");
        Response response = client.target(listURI).request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntityTag());
        RestFileInfo fileInfo = response.readEntity(RestFileInfo.class);
        return fileInfo;
    }

    private RestFileInfo info(Client client, String file) {
        URI infoURI = testServer.getServerURI().resolve("rest/info/"+file);
        Response response = client.target(infoURI).request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntityTag());
        RestFileInfo fileInfo = response.readEntity(RestFileInfo.class);
        return fileInfo;
    }
    
}
