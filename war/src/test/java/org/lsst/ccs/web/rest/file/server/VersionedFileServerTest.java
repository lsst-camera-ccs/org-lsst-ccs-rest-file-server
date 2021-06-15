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
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.lsst.ccs.web.rest.file.server.data.Constants.PROTOCOL_VERSION_HEADER;
import org.lsst.ccs.web.rest.file.server.data.VersionInfo;
import org.lsst.ccs.web.rest.file.server.data.VersionInfoV2;

/**
 *
 * @author tonyj
 */
public class VersionedFileServerTest {

    private static TestServer testServer;

    public VersionedFileServerTest() {
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

            VersionInfo fileInfo4 = info(client, testFile);
            assertEquals("1", fileInfo4.getVersions().get(0).getName());
            assertEquals(content.length(), fileInfo4.getVersions().get(0).getSize());
            assertEquals("text/plain", fileInfo4.getVersions().get(0).getMimeType());

            VersionInfoV2 fileInfo5 = info2(client, testFile);
            assertEquals("1", fileInfo5.getVersions().get(0).getName());
            assertEquals(content.length(), fileInfo5.getVersions().get(0).getSize());
            assertEquals("text/plain", fileInfo5.getVersions().get(0).getMimeType());
            assertFalse(fileInfo5.getVersions().get(0).isHidden());
               
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
        URI downloadURI = testServer.getServerURI().resolve("rest/version/download/" + testFile);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(downloadURI.toURL().openStream()))) {
            String line = reader.readLine();
            assertEquals(content, line);
        }
    }

    private void upload(final String testFile, final String content) throws IOException, ProtocolException {
        // upload a versioned file
        URI uploadURI = testServer.getServerURI().resolve("rest/version/upload/" + testFile);
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
        URI deleteURI = testServer.getServerURI().resolve("rest/version/deleteFile/" + file);
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

    private VersionInfo info(Client client, String file) {
        URI infoURI = testServer.getServerURI().resolve("rest/version/info/"+file);
        Response response = client.target(infoURI).request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntityTag());
        VersionInfo fileInfo = response.readEntity(VersionInfo.class);
        return fileInfo;
    }

    private VersionInfoV2 info2(Client client, String file) {
        final Client client2 = ClientBuilder.newClient();
        client2.register(new AddProtcolVersionRequestFilter());
        URI infoURI = testServer.getServerURI().resolve("rest/version/info/"+file);
        Response response = client2.target(infoURI).request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntityTag());
        VersionInfoV2 fileInfo = response.readEntity(VersionInfoV2.class);
        return fileInfo;
    }
    
    private static class AddProtcolVersionRequestFilter implements ClientRequestFilter {
        public static final String FILTER_HEADER_VALUE = "2";
        public static final String FILTER_HEADER_KEY = PROTOCOL_VERSION_HEADER;

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            requestContext.getHeaders().add(FILTER_HEADER_KEY, FILTER_HEADER_VALUE);
        }    
    }
    
}
