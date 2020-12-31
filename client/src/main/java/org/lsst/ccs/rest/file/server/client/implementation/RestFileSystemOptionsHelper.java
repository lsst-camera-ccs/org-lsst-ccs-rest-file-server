package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.File;
import java.util.Map;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;

/**
 *
 * @author tonyj
 */
class RestFileSystemOptionsHelper {

    private final Map<String, ?> env;

    RestFileSystemOptionsHelper(Map<String, ?> env) {
        this.env = env;
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
    
    File getDiskCacheLocation() {
        Object result = env.get(RestFileSystemOptions.CACHE_LOCATION);
        if (result == null) {
            return null;
        } else if (result instanceof File) {
            return (File) result;
        } else if (result instanceof String) {
            return new File(((String) result));
        } else {
            throw new IllegalArgumentException("Invalid value for option " + RestFileSystemOptions.CACHE_LOCATION + ": " + result);
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
        }
        throw new IllegalArgumentException("Invalid value for option " + optionName + ": " + result);
    }
}
