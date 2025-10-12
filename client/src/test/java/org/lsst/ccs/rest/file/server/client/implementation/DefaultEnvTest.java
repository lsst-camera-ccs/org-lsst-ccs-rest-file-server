package org.lsst.ccs.rest.file.server.client.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 *
 * @author tonyj
 */
public class DefaultEnvTest {


    @Test
    public void defaultEnvTest() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> env = new LinkedHashMap<>();
        env.put(RestFileSystemOptions.USE_SSL, RestFileSystemOptions.SSLOptions.AUTO);
        env.put(RestFileSystemOptions.CACHE_FALLBACK, RestFileSystemOptions.CacheFallback.OFFLINE);
        env.put(RestFileSystemOptions.ALLOW_ALTERNATE_CACHE_LOCATION, true);
        env.put(RestFileSystemOptions.CACHE_LOCATION, new File("/tmp"));
        String json = objectMapper.writeValueAsString(env);
        System.out.println(json);
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY, json);
        RestFileSystemOptionsHelper restFileSystemOptionsHelper = new RestFileSystemOptionsHelper(null);
        assertEquals(RestFileSystemOptions.SSLOptions.AUTO, restFileSystemOptionsHelper.isUseSSL());
        assertEquals(RestFileSystemOptions.CacheFallback.OFFLINE, restFileSystemOptionsHelper.getCacheFallback());
        assertTrue(restFileSystemOptionsHelper.allowAlternateCacheLoction());
        assertEquals("/tmp", restFileSystemOptionsHelper.getDiskCacheLocation().toAbsolutePath().toString());
    }
}
