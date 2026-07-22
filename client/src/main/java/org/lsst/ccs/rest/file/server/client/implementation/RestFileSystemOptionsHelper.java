package org.lsst.ccs.rest.file.server.client.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 * Utility wrapper around the environment map supplied when creating the REST
 * file system. Provides typed accessors for the various supported options.
 */
 class RestFileSystemOptionsHelper {

    private final Map<String, ?> env;
    private static final Logger LOG = Logger.getLogger(RestFileSystemOptionsHelper.class.getName());
    private static final URI defaultMountPoint = URI.create(".");

    /**
     * Creates a new helper.
     * <p>
     * Options are resolved per key with the following precedence, highest
     * first: the explicitly supplied {@code env}, then the programmatic default
     * set via {@link RestFileSystemOptions#setDefaultFileSystemEnvironment}, then
     * the {@link RestFileSystemOptions#DEFAULT_ENV_PROPERTY} system-property JSON
     * map, and finally the hardcoded fallback applied by {@link #getOption}. A
     * key present at a higher level overrides the same key at a lower level; keys
     * absent at a higher level fall through.
     *
     * @param env environment map supplied to the file system; may be {@code null}
     */
    RestFileSystemOptionsHelper(Map<String, ?> env) {
        Map<String, Object> merged = new HashMap<>();
        Map<String, ?> propertyDefaults = parseDefaultEnvProperty();
        if (propertyDefaults != null) {
            merged.putAll(propertyDefaults);
        }
        Map<String, ?> programmaticDefaults = RestFileSystemProvider.getDefaultFileSystemOption();
        if (programmaticDefaults != null) {
            merged.putAll(programmaticDefaults);
        }
        if (env != null) {
            merged.putAll(env);
        }
        this.env = merged;
    }

    /**
     * Returns the configured cache option.
     *
     * @return cache option, never {@code null}
     */
    RestFileSystemOptions.CacheOptions getCacheOptions() {
        return getOption(RestFileSystemOptions.CACHE_OPTIONS, RestFileSystemOptions.CacheOptions.class, RestFileSystemOptions.CacheOptions.NONE);
    }

    /**
     * Determines whether SSL should be used when contacting the server.
     *
     * @return SSL option
     */
    RestFileSystemOptions.SSLOptions isUseSSL() {
        return getOption(RestFileSystemOptions.USE_SSL, RestFileSystemOptions.SSLOptions.class, RestFileSystemOptions.SSLOptions.AUTO);
    }

    /**
     * Returns the JWT authorization token, resolved through the option cascade.
     *
     * @return the token, or {@code null} if none was configured
     */
    String getAuthToken() {
        Object result = env.get(RestFileSystemOptions.AUTH_TOKEN);
        return result == null ? null : result.toString();
    }

    /**
     * Returns the cache fallback behaviour.
     *
     * @return cache fallback option
     */
    RestFileSystemOptions.CacheFallback getCacheFallback() {
        return getOption(RestFileSystemOptions.CACHE_FALLBACK, RestFileSystemOptions.CacheFallback.class, RestFileSystemOptions.CacheFallback.OFFLINE);
    }

    /**
     * Indicates whether cache logging is enabled.
     *
     * @return {@code true} if cache logging is enabled
     */
    boolean isCacheLogging() {
        return getOption(RestFileSystemOptions.CACHE_LOGGING, Boolean.class, Boolean.FALSE);
    }

    /**
     * The on-disk cache location and the spill flag ({@code CacheFallbackLocation})
     * describe the single per-JVM cache (see ADR 0003), so they are resolved
     * globally and once, not per file system. See
     * {@link #getGlobalCacheLocation()} and {@link #allowAlternateCacheLocation()}.
     * The per-file-system {@code env} is deliberately not consulted for these two
     * keys, which is what makes a per-FS {@code cacheLocation()} /
     * {@code ignoreLockedCache()} inert.
     */

    /** Built-in default cache location when nothing else supplies one. */
    private static final String DEFAULT_CACHE_LOCATION = "~/ccs/cache/default";

    // The global cache config is resolved once (before the first file system)
    // and memoized. A test backdoor can seed/reset it; see
    // setGlobalCacheConfigForTest / resetGlobalCacheConfigForTest.
    private static Path globalCacheLocation;
    private static boolean globalAllowAlternate;
    private static boolean globalResolved = false;

    // Programmatic location override (e.g. the CLI's --cacheDir), allowed only
    // before the cache is configured. Takes precedence over the property; the
    // spill flag still resolves normally.
    private static Path cacheLocationOverride;

    /**
     * Resolves the JVM-global cache location, once. Resolution order, highest
     * precedence first: the test backdoor, the {@link RestFileSystemOptions#DEFAULT_ENV_PROPERTY}
     * JSON map, then the built-in default {@value #DEFAULT_CACHE_LOCATION}. A
     * leading {@code ~} is expanded and the path normalized.
     *
     * @return the resolved global cache location, never {@code null}
     */
    static synchronized Path getGlobalCacheLocation() {
        resolveGlobalCacheConfig();
        return globalCacheLocation;
    }

    /**
     * Indicates whether the cache may spill to an alternate location
     * ({@code <name>-N}) when another process holds the lock on the resolved
     * location. JVM-global; resolved from the {@code CacheFallbackLocation} key
     * of the {@link RestFileSystemOptions#DEFAULT_ENV_PROPERTY} map, default
     * {@code false}.
     *
     * @return {@code true} if alternate locations are allowed
     */
    static synchronized boolean allowAlternateCacheLocation() {
        resolveGlobalCacheConfig();
        return globalAllowAlternate;
    }

    private static void resolveGlobalCacheConfig() {
        if (globalResolved) {
            return;
        }
        Map<String, ?> defaults = parseDefaultEnvProperty();
        // Location precedence: programmatic override, then the property, then the
        // built-in default. The spill flag always resolves from the property.
        Object location = cacheLocationOverride != null ? cacheLocationOverride
                : (defaults == null ? null : defaults.get(RestFileSystemOptions.CACHE_LOCATION));
        globalCacheLocation = toPath(location != null ? location : DEFAULT_CACHE_LOCATION);
        Object allow = defaults == null ? null : defaults.get(RestFileSystemOptions.ALLOW_ALTERNATE_CACHE_LOCATION);
        globalAllowAlternate = allow != null && Boolean.parseBoolean(allow.toString());
        globalResolved = true;
    }

    /**
     * Pins a programmatic cache-location override, allowed only while the cache
     * is still unconfigured (before the first file system is created). Once a
     * cache has been configured the location is fixed for the JVM, so a later
     * call fails rather than silently having no effect. Backs
     * {@link RestFileSystemOptions#setCacheLocation(Path)}.
     *
     * @param location the cache location to use for this JVM
     * @throws IllegalStateException if the cache location is already configured
     */
    static synchronized void setCacheLocationOverride(Path location) {
        if (globalResolved) {
            throw new IllegalStateException("Cache location cannot be set: the cache is already configured at " + globalCacheLocation);
        }
        cacheLocationOverride = location;
    }

    /**
     * Seeds the JVM-global cache config directly, bypassing the system property.
     * For tests only, so they need not manipulate a global {@code -D} — set a
     * per-test temp location here and {@link #resetGlobalCacheConfigForTest()}
     * afterwards.
     *
     * @param location the cache location to use
     * @param allowAlternate whether spill to an alternate location is allowed
     */
    static synchronized void setGlobalCacheConfigForTest(Path location, boolean allowAlternate) {
        globalCacheLocation = location;
        globalAllowAlternate = allowAlternate;
        globalResolved = true;
    }

    /** Clears the memoized global cache config so it is re-resolved. Tests must call this to avoid leaking state. */
    static synchronized void resetGlobalCacheConfigForTest() {
        globalCacheLocation = null;
        globalAllowAlternate = false;
        globalResolved = false;
        cacheLocationOverride = null;
    }

    /**
     * Converts a cache-location option value ({@link Path}, {@link File}, or a
     * {@link String} with a leading {@code ~} and {@code .}/{@code ..} segments)
     * to a normalized {@link Path}. Java has no shell {@code ~} expansion, so a
     * string value (e.g. from the JSON property or the built-in default) is
     * expanded here; only a leading {@code ~} or {@code ~/} is expanded,
     * {@code ~user} is left untouched.
     */
    private static Path toPath(Object value) {
        if (value instanceof Path) {
            return (Path) value;
        } else if (value instanceof File) {
            return ((File) value).toPath();
        } else if (value instanceof String) {
            return Paths.get(expandTilde((String) value)).normalize();
        } else {
            throw new IllegalArgumentException("Invalid value for option " + RestFileSystemOptions.CACHE_LOCATION + ": " + value);
        }
    }

    private static String expandTilde(String path) {
        if (path.equals("~")) {
            return System.getProperty("user.home");
        } else if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    /**
     * Returns the mount point that should be considered the root of the server.
     *
     * @return mount point URI
     */
    URI getMountPoint() {
        Object result = env.get(RestFileSystemOptions.MOUNT_POINT);
        if (result == null) {
            return defaultMountPoint;
        } else if (result instanceof URI) {
            return (URI) result;
        } else if (result instanceof String) {
            return URI.create((String) result);
        } else {
            throw new IllegalArgumentException("Invalid value for option " + RestFileSystemOptions.MOUNT_POINT + ": " + result);
        }
    }

    private <T> T getOption(String optionName, Class<T> type, T defaultValue) {
        Object result = env.get(optionName);
        if (result == null) {
            result = defaultValue;
        }
        if (type.isInstance(result)) {
            return type.cast(result);
        } else if (result instanceof String) {
            try {
                Method method = type.getMethod("valueOf", String.class);
                if (type.isAssignableFrom(method.getReturnType())) {
                    return type.cast(method.invoke(null, result));
                } 
            } catch (ReflectiveOperationException x) {
                // Just fall through to the IllegalArgumentException
            }
        }
        throw new IllegalArgumentException("Invalid value for option " + optionName + ": " + result);
    }

    private static Map<String, ?> parseDefaultEnvProperty() {
        String defaultOptions = System.getProperty(RestFileSystemOptions.DEFAULT_ENV_PROPERTY);
        if (defaultOptions != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                return objectMapper.readValue(defaultOptions, new TypeReference<Map<String,Object>>(){});
            } catch (JsonProcessingException x) {
                LOG.log(Level.WARNING, "Unable to parse default rest server options: "+defaultOptions, x);
            }
        }
        return null;
    }
}
