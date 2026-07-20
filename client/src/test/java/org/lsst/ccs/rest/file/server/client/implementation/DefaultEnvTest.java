package org.lsst.ccs.rest.file.server.client.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
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
    
    @Test
    public void defaultEnvTest1() {
        String jsonEnv = " {\"CacheOptions\":\"MEMORY_AND_DISK\",\"CacheLocation\":\"~/ccs/cache/remoteFileSystem\"}";
        System.setProperty("org.lsst.ccs.rest.file.client.defaultEnvironment", jsonEnv);
        
        RestFileSystemOptionsHelper optionsHelper = new RestFileSystemOptionsHelper(null);
        
        assertEquals(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK, optionsHelper.getCacheOptions());
        assertEquals(optionsHelper.getDiskCacheLocation().toString(),"~/ccs/cache/remoteFileSystem");
        
        
        
        System.setProperty("org.lsst.ccs.rest.file.client.defaultEnvironment", "");

    }

    /**
     * Clears both static default sources so cascade tests don't leak into each
     * other or into the rest of the suite.
     */
    @AfterEach
    public void clearDefaults() {
        System.clearProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY);
        RestFileSystemProvider.setDefaultFileSystemOption(null);
    }

    /**
     * A key in the explicit env overrides the same key from the system-property
     * default, while keys absent from the explicit env still fall through to it.
     */
    @Test
    public void explicitOverridesPropertyPerKey() {
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY,
                "{\"CacheOptions\":\"MEMORY_AND_DISK\",\"CacheFallback\":\"OFFLINE\"}");

        Map<String, Object> env = new HashMap<>();
        env.put(RestFileSystemOptions.CACHE_FALLBACK, RestFileSystemOptions.CacheFallback.ALWAYS);

        RestFileSystemOptionsHelper helper = new RestFileSystemOptionsHelper(env);
        // Overridden by the explicit env.
        assertEquals(RestFileSystemOptions.CacheFallback.ALWAYS, helper.getCacheFallback());
        // Fell through from the system property.
        assertEquals(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK, helper.getCacheOptions());
    }

    /**
     * Precedence is explicit env, then programmatic default, then system
     * property, then the hardcoded fallback.
     */
    @Test
    public void cascadePrecedence() {
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY,
                "{\"CacheOptions\":\"MEMORY_ONLY\",\"CacheFallback\":\"NEVER\"}");
        Map<String, Object> programmatic = new HashMap<>();
        programmatic.put(RestFileSystemOptions.CACHE_OPTIONS, RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK);
        RestFileSystemProvider.setDefaultFileSystemOption(programmatic);

        Map<String, Object> env = new HashMap<>();
        env.put(RestFileSystemOptions.CACHE_FALLBACK, RestFileSystemOptions.CacheFallback.ALWAYS);

        RestFileSystemOptionsHelper helper = new RestFileSystemOptionsHelper(env);
        // Explicit env wins.
        assertEquals(RestFileSystemOptions.CacheFallback.ALWAYS, helper.getCacheFallback());
        // Programmatic default beats the system property.
        assertEquals(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK, helper.getCacheOptions());
        // Nothing set this anywhere but the hardcoded fallback.
        assertEquals(RestFileSystemOptions.SSLOptions.AUTO, helper.isUseSSL());
    }

    /**
     * With no explicit env, no programmatic default, and no system property,
     * the hardcoded fallbacks apply.
     */
    @Test
    public void hardcodedFallbackWhenNothingConfigured() {
        RestFileSystemOptionsHelper helper = new RestFileSystemOptionsHelper(null);
        assertEquals(RestFileSystemOptions.CacheOptions.NONE, helper.getCacheOptions());
        assertEquals(RestFileSystemOptions.CacheFallback.OFFLINE, helper.getCacheFallback());
    }
}
