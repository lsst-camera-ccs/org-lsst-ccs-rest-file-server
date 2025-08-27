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

    /**
     * Caching strategies for the client.
     */
    public enum CacheOptions {
        /** Do not cache any data locally. */
        NONE,
        /** Cache data in memory only. */
        MEMORY_ONLY,
        /** Cache data in memory and on disk. */
        MEMORY_AND_DISK
    };

    /**
     * Fallback behavior when a cache cannot be accessed.
     */
    public enum CacheFallback {
        /** Never fall back to an alternate cache location. */
        NEVER,
        /** Use the alternate cache only when offline. */
        OFFLINE,
        /** Always use the alternate cache if available. */
        ALWAYS,
        /** Use the alternate cache when possible. */
        WHEN_POSSIBLE;
    };

    /**
     * Control over use of SSL connections.
     */
    public enum SSLOptions {
        /** Always use SSL. */
        TRUE,
        /** Never use SSL. */
        FALSE,
        /** Decide automatically based on URI scheme. */
        AUTO
    };

    /**
     * Creates a builder for assembling an environment map.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder used to construct the environment map passed to
     * {@code RestFileSystemProvider}.
     */
    public static class Builder {
        private final Map<String, Object> map = new HashMap<>();

        /**
         * Specifies the cache location using a {@link File} path.
         *
         * @param location directory where the cache should be stored
         * @return this builder for method chaining
         */
        public Builder cacheLocation(File location) {
            map.put(CACHE_LOCATION, location);
            return this;
        }

        /**
         * Specifies the cache location using a {@link Path}.
         *
         * @param location path where the cache should be stored
         * @return this builder for method chaining
         */
        public Builder cacheLocation(Path location) {
            map.put(CACHE_LOCATION, location);
            return this;
        }

        /**
         * Enables or disables cache activity logging.
         *
         * @param log {@code true} to enable logging
         * @return this builder for method chaining
         */
        public Builder logging(boolean log) {
            map.put(CACHE_LOGGING, log);
            return this;
        }

        /**
         * Sets the caching strategy.
         *
         * @param option cache option to apply
         * @return this builder for method chaining
         */
        public Builder set(CacheOptions option) {
            map.put(CACHE_OPTIONS, option);
            return this;
        }

        /**
         * Sets the SSL usage option.
         *
         * @param option SSL option to apply
         * @return this builder for method chaining
         */
        public Builder set(SSLOptions option) {
            map.put(USE_SSL, option);
            return this;
        }

        /**
         * Sets the cache fallback behavior.
         *
         * @param option fallback option to use
         * @return this builder for method chaining
         */
        public Builder set(CacheFallback option) {
            map.put(CACHE_FALLBACK, option);
            return this;
        }

        /**
         * Supplies an authorization token to be used with requests.
         *
         * @param token JWT authorization token
         * @return this builder for method chaining
         */
        public Builder setAuthorizationToken(String token) {
            map.put(AUTH_TOKEN, token);
            return this;
        }

        /**
         * Allows the cache to fall back to an alternate location when locked.
         *
         * @param allow {@code true} to permit alternate cache locations
         * @return this builder for method chaining
         */
        public Builder ignoreLockedCache(boolean allow) {
            map.put(ALLOW_ALTERNATE_CACHE_LOCATION, allow);
            return this;
        }

        /**
         * Sets the mount point URI for the file system.
         *
         * @param mountPoint URI identifying the desired mount point
         * @return this builder for method chaining
         */
        public Builder mountPoint(URI mountPoint) {
            map.put(MOUNT_POINT, mountPoint);
            return this;
        }

        /**
         * Builds the environment map containing all configured options.
         *
         * @return map of file system options
         */
        public Map<String, Object> build() {
            return map;
        }
    }

    /**
     * Provide a default environment to be used when no explicit environment is
     * supplied to the {@code newFileSystem} methods.
     *
     * @param defaultEnv map of default options
     */
    public static void setDefaultFileSystemEnvironment(Map<String, ?> defaultEnv) {
        RestFileSystemProvider.setDefaultFileSystemOption(defaultEnv);
    }
}
