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
        // CacheLocation and the spill flag are now resolved globally (ADR 0003).
        assertTrue(RestFileSystemOptionsHelper.allowAlternateCacheLocation());
        assertEquals("/tmp", RestFileSystemOptionsHelper.getGlobalCacheLocation().toAbsolutePath().toString());
    }

    @Test
    public void defaultEnvTest1() {
        String jsonEnv = " {\"CacheOptions\":\"MEMORY_AND_DISK\",\"CacheLocation\":\"~/ccs/cache/remoteFileSystem\"}";
        System.setProperty("org.lsst.ccs.rest.file.client.defaultEnvironment", jsonEnv);

        RestFileSystemOptionsHelper optionsHelper = new RestFileSystemOptionsHelper(null);

        assertEquals(RestFileSystemOptions.CacheOptions.MEMORY_AND_DISK, optionsHelper.getCacheOptions());
        // The leading ~ is expanded to the user's home directory.
        assertEquals(System.getProperty("user.home") + "/ccs/cache/remoteFileSystem",
                RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());
    }

    /**
     * A bare {@code ~} expands to the user's home directory; a path with no
     * leading tilde is left untouched (aside from lexical normalization). The
     * global location is resolved once per JVM, so we reset between cases.
     */
    @Test
    public void tildeExpansion() {
        String home = System.getProperty("user.home");

        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY, "{\"CacheLocation\":\"~\"}");
        assertEquals(home, RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());

        RestFileSystemOptionsHelper.resetGlobalCacheConfigForTest();
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY, "{\"CacheLocation\":\"/var/lib/ccs/rest-cache\"}");
        assertEquals("/var/lib/ccs/rest-cache", RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());

        // ../ and ./ are collapsed lexically by normalize().
        RestFileSystemOptionsHelper.resetGlobalCacheConfigForTest();
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY, "{\"CacheLocation\":\"/var/lib/ccs/../ccs/rest-cache\"}");
        assertEquals("/var/lib/ccs/rest-cache", RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());
    }

    /**
     * Clears the static default sources so cascade tests don't leak into each
     * other or into the rest of the suite.
     */
    @AfterEach
    public void clearDefaults() {
        System.clearProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY);
        RestFileSystemProvider.setDefaultFileSystemOption(null);
        RestFileSystemOptionsHelper.resetGlobalCacheConfigForTest();
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

    /**
     * The cache location is JVM-global (ADR 0003): a per-file-system
     * {@code CacheLocation} in the env is ignored, so the toolkit's per-mount
     * call is inert. Only the global property (or built-in default) is honored.
     */
    @Test
    public void perFsCacheLocationIsIgnored() {
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY,
                "{\"CacheLocation\":\"/var/lib/ccs/global-cache\"}");
        Map<String, Object> env = new HashMap<>();
        env.put(RestFileSystemOptions.CACHE_LOCATION, new File("/tmp/per-fs-should-be-ignored"));
        // Construct a helper with the per-FS value present; it must not affect the global location.
        new RestFileSystemOptionsHelper(env);
        assertEquals("/var/lib/ccs/global-cache",
                RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());
    }

    /**
     * With no property and no backdoor, the global location is the built-in
     * default under the user's home.
     */
    @Test
    public void builtInDefaultCacheLocation() {
        assertEquals(System.getProperty("user.home") + "/ccs/cache/default",
                RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());
    }

    /**
     * A programmatic location override (the CLI's {@code --cacheDir}) wins over
     * the property, and the spill flag still resolves from the property.
     */
    @Test
    public void cacheLocationOverrideWinsBeforeConfigured() {
        System.setProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY,
                "{\"CacheLocation\":\"/var/lib/ccs/from-property\",\"CacheFallbackLocation\":true}");
        RestFileSystemOptionsHelper.setCacheLocationOverride(java.nio.file.Paths.get("/tmp/from-cli"));

        assertEquals("/tmp/from-cli", RestFileSystemOptionsHelper.getGlobalCacheLocation().toString());
        // Spill flag still comes from the property, not reset by the override.
        assertTrue(RestFileSystemOptionsHelper.allowAlternateCacheLocation());
    }

    /**
     * Once the cache is configured (location resolved), a later override attempt
     * fails rather than silently having no effect.
     */
    @Test
    public void cacheLocationOverrideRejectedAfterConfigured() {
        // Resolve the location, marking the cache configured.
        RestFileSystemOptionsHelper.getGlobalCacheLocation();
        try {
            RestFileSystemOptionsHelper.setCacheLocationOverride(java.nio.file.Paths.get("/tmp/too-late"));
            org.junit.jupiter.api.Assertions.fail("expected IllegalStateException");
        } catch (IllegalStateException x) {
            assertTrue(x.getMessage().contains("already configured"), x.getMessage());
        }
    }
}
