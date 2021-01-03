package org.lsst.ccs.rest.file.server.client;

import java.nio.file.OpenOption;

/**
 * Used to specify which version of a versioned file to read.
 * @author tonyj
 */
public class VersionOpenOption implements OpenOption {
    public static VersionOpenOption LATEST = new VersionOpenOption("latest");
    public static VersionOpenOption DEFAULT = new VersionOpenOption("default");
    private final String value;

    private VersionOpenOption(String version) {
       this.value = version; 
    }
    public String value() {
        return value;
    }
    public static VersionOpenOption of(int version) {
        return new VersionOpenOption(String.valueOf(version));
    } 
    
    public static VersionOpenOption of(String version) {
        if (LATEST.value.equals(version)) return LATEST;
        if (DEFAULT.value.equals(version)) return DEFAULT;
        try {
            return VersionOpenOption.of(Integer.parseInt(version)); 
        } catch (NumberFormatException x) {
            throw new IllegalArgumentException("Invalid version: "+version, x);
        }
    } 
}
