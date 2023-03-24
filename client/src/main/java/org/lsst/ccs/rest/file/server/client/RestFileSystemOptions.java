package org.lsst.ccs.rest.file.server.client;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.lsst.ccs.rest.file.server.client.implementation.RestFileSystemProvider;

/**
 * Options which can be passed as part of the environment argument when
 * explicitly creating a RestFileSystem.
 *
 * @author tonyj
 */
public class RestFileSystemOptions {

    public final static String CACHE_OPTIONS = "CacheOptions";
    public final static String CACHE_FALLBACK = "CacheFallback";
    public final static String CACHE_LOGGING = "CacheLogging";
    public final static String USE_SSL = "UseSSL";
    public final static String CACHE_LOCATION = "CacheLocation";
    public final static String ALLOW_ALTERNATE_CACHE_LOCATION = "CacheFallbackLocation";
    public final static String AUTH_TOKEN = "JWTToken";
    public final static String MOUNT_POINT = "MountPoint";

    /**
     * A system property which can be set to provide a default set of options if no explicit options are 
     * set and if none have been provided by <code>setDefaultFileSystemEnvironment</code> method. The value
     * of the system property, if set, should be a JSON string representation of a Map. If the Map is 
     * invalid a WARNING will be issued, but the map will otherwise be ignored.
     */
    public final static String DEFAULT_ENV_PROPERTY = "org.lsst.ccs.rest.file.client.defaultEnvironment";

    public enum CacheOptions {
        NONE, MEMORY_ONLY, MEMORY_AND_DISK
    };

    public enum CacheFallback {
        NEVER, OFFLINE, WHEN_POSSIBLE, ALWAYS
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

        public Builder cacheLocation(Path location) {
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
        
        public Builder setAuthorizationToken(String token) {
            map.put(AUTH_TOKEN, token);
            return this;
        }
        
        public Builder ignoreLockedCache(boolean allow) {
            map.put(ALLOW_ALTERNATE_CACHE_LOCATION, allow);
            return this;            
        }

        public Builder mountPoint(URI mountPoint) {
            map.put(MOUNT_POINT, mountPoint);
            return this;            
        }
        
        public Map<String, Object> build() {
            return map;
        }
    }
    /**
     * Provide a default environment to be used in the event that no explicit
     * environment is given when creating the RestFileSystem.
     * @param defaultEnv 
     */
    public static void setDefaultFileSystemEnvironment(Map<String, ?> defaultEnv) {
        RestFileSystemProvider.setDefaultFileSystemOption(defaultEnv);
    }
}
