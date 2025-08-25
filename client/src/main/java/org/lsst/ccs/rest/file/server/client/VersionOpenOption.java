package org.lsst.ccs.rest.file.server.client;

import java.nio.file.OpenOption;

/**
 * Used to specify which version of a versioned file to read.
 *
 * @author tonyj
 */
public class VersionOpenOption implements OpenOption {

    /** Option representing the latest available version. */
    public static VersionOpenOption LATEST = new VersionOpenOption("latest");
    /** Option representing the default version. */
    public static VersionOpenOption DEFAULT = new VersionOpenOption("default");
    private final String value;

    private VersionOpenOption(String version) {
        this.value = version;
    }

    /**
     * Returns the string value representing this option.
     *
     * @return version token
     */
    public String value() {
        return value;
    }

    /**
     * Creates an option referencing a specific version number.
     *
     * @param version explicit version number
     * @return option representing the given version
     */
    public static VersionOpenOption of(int version) {
        return new VersionOpenOption(String.valueOf(version));
    }

    /**
     * Parses a string into a {@code VersionOpenOption}.
     * Recognizes {@code "latest"}, {@code "default"}, or a numeric version.
     *
     * @param version string representation of the version
     * @return corresponding option instance
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    public static VersionOpenOption of(String version) {
        if (LATEST.value.equals(version)) {
            return LATEST;
        }
        if (DEFAULT.value.equals(version)) {
            return DEFAULT;
        }
        try {
            return VersionOpenOption.of(Integer.parseInt(version));
        } catch (NumberFormatException x) {
            throw new IllegalArgumentException("Invalid version: " + version, x);
        }
    }

    /**
     * Resolves the numeric version referenced by this option using file attributes.
     *
     * @param attributes attributes providing default and latest versions
     * @return resolved integer version
     */
    public int getIntVersion(VersionedFileAttributes attributes) {
        if (VersionOpenOption.LATEST.value.equals(value)) {
            return attributes.getLatestVersion();
        } else if (VersionOpenOption.DEFAULT.value.equals(value)) {
            return attributes.getDefaultVersion();
        } else {
            return Integer.parseInt(value);
        }
    }

}
