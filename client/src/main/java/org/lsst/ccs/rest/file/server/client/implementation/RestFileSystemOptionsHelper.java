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
        Map<String, ?> propertyDefaults = createDefaultOptions();
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
     * Specifies whether alternate cache locations are permitted if the primary
     * location is unavailable.
     *
     * @return {@code true} if alternate locations are allowed
     */
    boolean allowAlternateCacheLoction() {
        return getOption(RestFileSystemOptions.ALLOW_ALTERNATE_CACHE_LOCATION, Boolean.class, Boolean.FALSE);
    }

    /**
     * Provides the location of the on-disk cache if configured.
     *
     * @return path to the disk cache or {@code null} if none
     */
    Path getDiskCacheLocation() {
        Object result = env.get(RestFileSystemOptions.CACHE_LOCATION);
        if (result == null) {
            return null;
        } else if (result instanceof Path) {
            return (Path) result;
        } else if (result instanceof File) {
            return ((File) result).toPath();
        } else if (result instanceof String) {
            return Paths.get(expandTilde((String) result)).normalize();
        } else {
            throw new IllegalArgumentException("Invalid value for option " + RestFileSystemOptions.CACHE_LOCATION + ": " + result);
        }
    }

    /**
     * Expands a leading {@code ~} to the user's home directory. Unlike a shell,
     * Java does not resolve {@code ~} in a path string, so a value that reaches
     * us unexpanded (e.g. from the {@link RestFileSystemOptions#DEFAULT_ENV_PROPERTY}
     * JSON map) must be handled here. Only a leading {@code ~} (alone or followed
     * by a separator) is expanded; {@code ~user} and non-leading tildes are left
     * untouched.
     *
     * @param path the raw path string
     * @return the path with a leading {@code ~} replaced by {@code user.home}
     */
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

    private Map<String, ?> createDefaultOptions() {
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
