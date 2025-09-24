package org.lsst.ccs.rest.file.server.client.implementation;

import java.io.IOException;
import java.util.Properties;

/**
 * A Class to build a single static Cache.
 * 
 */
abstract class CacheBuilder {

    
    private static Cache cacheInstance;
    private static Properties cacheProperties = new Properties();
    private static RestFileSystemOptionsHelper cacheOptions = new RestFileSystemOptionsHelper(null);

    
    
    public static void addRegion(String cacheRegion, RestFileSystemOptionsHelper options) {
        cacheOptions.mergeOptions(options);
        cacheProperties.setProperty("jcs.region."+cacheRegion, "DC");
        cacheProperties.setProperty("jcs.region."+cacheRegion+".cacheattributes", "org.apache.commons.jcs3.engine.CompositeCacheAttributes");
        cacheProperties.setProperty("jcs.region."+cacheRegion+".cacheattributes.MaxObjects", "1000");
        cacheProperties.setProperty("jcs.region."+cacheRegion+".cacheattributes.MemoryCacheName", "org.apache.commons.jcs3.engine.memory.lru.LRUMemoryCache");        
    }
    
    public static void reset() {
        cacheProperties = new Properties();
        cacheOptions = new RestFileSystemOptionsHelper(null);
    }

    public static Cache getCache() {
        if ( cacheInstance == null ) {
            try {
                cacheInstance = new Cache(cacheOptions, cacheProperties);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to build the cache.",ioe);
            }
        }
        return cacheInstance;
    }

}
