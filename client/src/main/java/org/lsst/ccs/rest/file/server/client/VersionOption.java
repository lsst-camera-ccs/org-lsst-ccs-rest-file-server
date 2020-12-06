package org.lsst.ccs.rest.file.server.client;

import java.nio.file.OpenOption;

/**
 *
 * @author tonyj
 */
public class VersionOption implements OpenOption {
    public static VersionOption LATEST = new VersionOption("latest");
    public static VersionOption DEFAULT = new VersionOption("default");
    private final String value;

    private VersionOption(String version) {
       this.value = version; 
    }
    public String value() {
        return value;
    }
    public static VersionOption of(int version) {
        return new VersionOption(String.valueOf(version));
    } 
}
