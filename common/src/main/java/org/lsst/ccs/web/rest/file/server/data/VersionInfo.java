package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents version information for a versioned file as returned by the REST
 * file server.
 *
 * @author tonyj
 */
public class VersionInfo implements Serializable {

    private final int defaultVersion;
    private final int latestVersion;
    private final List<VersionInfo.Version> versions;

    /**
     * Creates a new {@code VersionInfo} from JSON properties.
     *
     * @param defaultVersion default version number
     * @param latestVersion latest version number
     * @param versions list of available versions
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VersionInfo(@JsonProperty("default") int defaultVersion, @JsonProperty("latest") int latestVersion, @JsonProperty("versions")  List<VersionInfo.Version> versions) {
        this.defaultVersion = defaultVersion;
        this.latestVersion = latestVersion;
        this.versions = versions;
    }

    VersionInfo(VersionInfoV2 v2) {
        defaultVersion = v2.getDefault();
        latestVersion = v2.getLatest();
        List<Version> oldVersions = new ArrayList<>();
        for (VersionInfoV2.Version v : v2.getVersions()) {
            oldVersions.add(new Version(v));
        }
        this.versions = oldVersions;
    }

    /**
     * Gets the default version number.
     *
     * @return default version
     */
    public int getDefault() {
        return defaultVersion;
    }

    /**
     * Gets the latest version number.
     *
     * @return latest version
     */
    public int getLatest() {
        return latestVersion;
    }

    /**
     * Gets the list of available versions.
     *
     * @return list of version metadata
     */
    public List<Version> getVersions() {
        return versions;
    }

    /**
     * Metadata for a specific version of a file.
     */
    public static class Version extends RestFileInfo {

        private final int version;

        /**
         * Creates a {@code Version} from JSON properties.
         *
         * @param lastModified last modification time in milliseconds
         * @param creationTime creation time in milliseconds
         * @param lastAccessTime last access time in milliseconds
         * @param size size of the file in bytes
         * @param mimeType detected MIME type
         * @param name file name
         * @param fileKey unique file key
         * @param directory {@code true} if the path is a directory
         * @param other {@code true} if the path is some other type
         * @param regularFile {@code true} if the path is a regular file
         * @param symbolicLink {@code true} if the path is a symbolic link
         * @param versionedFile {@code true} if the path is a versioned file
         * @param children child entries if the path is a directory
         * @param version version number
         */
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Version(
            @JsonProperty("lastModified") long lastModified,
            @JsonProperty("creationTime") long creationTime,
            @JsonProperty("lastAccessTime") long lastAccessTime,
            @JsonProperty("size") long size,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("name") String name,
            @JsonProperty("fileKey") String fileKey,
            @JsonProperty("directory") boolean directory,
            @JsonProperty("other") boolean other,
            @JsonProperty("regularFile") boolean regularFile,
            @JsonProperty("symbolicLink") boolean symbolicLink,
            @JsonProperty("versionedFile") boolean versionedFile,
            @JsonProperty("children") List<RestFileInfo> children,
            @JsonProperty("version") int version) {
            super(lastModified, creationTime, lastAccessTime, size, mimeType, name, fileKey, directory, other, regularFile, symbolicLink, versionedFile, children);
            this.version = version;
        }

        /**
         * Creates a {@code Version} from a file.
         *
         * @param file file path
         * @param fileAttributes file attributes
         * @param version version number
         * @throws IOException if the content type cannot be determined
         */
        public Version(Path file, BasicFileAttributes fileAttributes, int version) throws IOException {
            super(file, fileAttributes, false);
            this.version = version;
        }

        /**
         * Copy constructor.
         *
         * @param other version to copy
         */
        public Version(Version other) {
            super(other);
            this.version = other.version;
        }

        /**
         * Creates a {@code Version} from a v2 representation.
         *
         * @param v2 v2 version information
         */
        public Version(VersionInfoV2.Version v2) {
            super(v2);
            this.version = v2.getVersion();
        }

        /**
         * Gets the version number.
         *
         * @return version number
         */
        public int getVersion() {
            return version;
        }
    }

    /**
     * Returns a debug representation of this version information.
     *
     * @return human readable representation
     */
    @Override
    public String toString() {
        return "VersionInfo{" + "defaultVersion=" + defaultVersion + ", latestVersion=" + latestVersion + ", versions=" + versions + '}';
    }

    /**
     * Converts the core fields of this instance into a map for convenient
     * serialization.
     *
     * @return map containing the latest and default version numbers
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("latestVersion", getLatest());
        result.put("defaultVersion", getDefault());
        return result;
    }

}
