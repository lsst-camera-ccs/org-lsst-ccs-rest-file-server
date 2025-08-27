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

    private Map<String, ?> env;
    private static final Logger LOG = Logger.getLogger(RestFileSystemOptionsHelper.class.getName());
    private static final URI defaultMountPoint = URI.create(".");

    /**
     * Creates a new helper.
     *
     * @param env environment map supplied to the file system; may be {@code null}
     */
    RestFileSystemOptionsHelper(Map<String, ?> env) {
        if (env == null) {
            this.env = createDefaultOptions();
        } else {
            this.env = env;
        }
    }

    /**
     * Merge the content of a RestFileSystemOptionsHelper into this instance.
     * 
     * @param options The options to be merged
     */
    void mergeOptions(RestFileSystemOptionsHelper options) {
        if ( this.env == null ) {
            this.env = options.env;
        } else {
            if (options.env != null) {
                this.env.putAll((Map) options.env);
            }
        }
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
            return Paths.get((String) result);
        } else {
            throw new IllegalArgumentException("Invalid value for option " + RestFileSystemOptions.CACHE_LOCATION + ": " + result);
        }
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
        // TODO: Fix unsafe cast (how?)
        if (type.isInstance(result)) {
            return type.cast(result);
        } else if (result instanceof String) {
            try {
                Method method = type.getMethod("valueOf", String.class);
                if (type.isAssignableFrom(method.getReturnType())) {
                    return (T) method.invoke(null, result);
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
