package org.lsst.ccs.rest.file.server.client.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 *
 * @author tonyj
 */
 class RestFileSystemOptionsHelper {

    private final Map<String, ?> env;
    private static final Logger LOG = Logger.getLogger(RestFileSystemOptionsHelper.class.getName());
    private static final URI defaultMountPoint = URI.create(".");

    RestFileSystemOptionsHelper(Map<String, ?> env) {
        if (env == null) {
            this.env = createDefaultOptions();
        } else {
            this.env = env;
        }
    }

    RestFileSystemOptions.CacheOptions getCacheOptions() {
        return getOption(RestFileSystemOptions.CACHE_OPTIONS, RestFileSystemOptions.CacheOptions.class, RestFileSystemOptions.CacheOptions.NONE);
    }

    RestFileSystemOptions.SSLOptions isUseSSL() {
        return getOption(RestFileSystemOptions.USE_SSL, RestFileSystemOptions.SSLOptions.class, RestFileSystemOptions.SSLOptions.AUTO);
    }

    RestFileSystemOptions.CacheFallback getCacheFallback() {
        return getOption(RestFileSystemOptions.CACHE_FALLBACK, RestFileSystemOptions.CacheFallback.class, RestFileSystemOptions.CacheFallback.OFFLINE);
    }

    boolean isCacheLogging() {
        return getOption(RestFileSystemOptions.CACHE_LOGGING, Boolean.class, Boolean.FALSE);
    }

    boolean allowAlternateCacheLoction() {
        return getOption(RestFileSystemOptions.ALLOW_ALTERNATE_CACHE_LOCATION, Boolean.class, Boolean.FALSE);
    }

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
