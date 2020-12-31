package org.lsst.ccs.rest.file.server.client;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Options which can be passed as part of the environment argument when
 * creating a RestFileSystem.
 *
 * @author tonyj
 */
public class RestFileSystemOptions {

    public final static String CACHE_OPTIONS = "CacheOptions";
    public final static String CACHE_FALLBACK = "CacheFallback";
    public final static String CACHE_LOGGING = "CacheLogging";
    public final static String USE_SSL = "UseSSL";
    public final static String CACHE_LOCATION = "CacheLocation";

    public enum CacheOptions {
        NONE, MEMORY_ONLY, MEMORY_AND_DISK
    };

    public enum CacheFallback {
        NEVER, OFFLINE, ALWAYS
    };
    
    public enum SSLOptions {
        TRUE, FALSE, AUTO
    };
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final Map<String, Object> map = new HashMap<>();
        
        public Builder cacheLocation(File location) {
            map.put(CACHE_LOCATION, location);
            return this;
        }

        public Builder logging(boolean log) {
            map.put(CACHE_LOGGING, log);
            return this;
        }

        public Builder set(CacheOptions option) {
            map.put(CACHE_OPTIONS, option);
            return this;
        }

        public Builder set(SSLOptions option) {
            map.put(USE_SSL, option);
            return this;
        }

        public Builder set(CacheFallback option) {
            map.put(CACHE_FALLBACK, option);
            return this;
        }
        
        public Map<String, Object> build() {
            return map;
        }
    }
}
