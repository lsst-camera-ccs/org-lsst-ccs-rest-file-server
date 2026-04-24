package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents version information for a file as returned by the REST file
 * server.
 *
 * @author tonyj
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionInfoV2 implements Serializable {

    private final int defaultVersion;
    private final int latestVersion;
    private final List<VersionInfoV2.Version> versions;
    private final List<DefaultChangeRecord> defaultHistory;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VersionInfoV2(@JsonProperty("default") int defaultVersion, @JsonProperty("latest") int latestVersion, @JsonProperty("versions") List<Version> versions, @JsonProperty("defaultHistory") List<DefaultChangeRecord> defaultHistory) {
        this.defaultVersion = defaultVersion;
        this.latestVersion = latestVersion;
        this.versions = versions;
        this.defaultHistory = defaultHistory == null ? Collections.emptyList() : defaultHistory;
    }

    public VersionInfoV2(int defaultVersion, int latestVersion, List<Version> versions) {
        this(defaultVersion, latestVersion, versions, Collections.emptyList());
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

    public List<DefaultChangeRecord> getDefaultHistory() {
        return defaultHistory;
    }

    /**
     * Downgrades this object to an older protocol version if necessary.
     *
     * @param protocolVersion requested protocol version
     * @return a compatible representation for the requested protocol version
     */
    public Serializable downgrade(Integer protocolVersion) {
        if (protocolVersion == null || protocolVersion<2) {
            return new VersionInfo(this);
        } else {
            return this;
        }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Version extends RestFileInfo {
        private final int version;
        private final boolean hidden;
        private final String comment;
        private final String creator;

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
         * @param hidden {@code true} if the version is hidden
         * @param comment version comment
         * @param creator creator of this version
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
            @JsonProperty("version") int version,
            @JsonProperty("hidden") boolean hidden,
            @JsonProperty("comment") String comment,
            @JsonProperty("creator") String creator) {

            super(lastModified, creationTime, lastAccessTime, size, mimeType, name, fileKey, directory, other, regularFile, symbolicLink, versionedFile, children);
            this.version = version;
            this.hidden = hidden;
            this.comment = comment == null ? "" : comment;
            this.creator = creator == null ? "" : creator;
        }

        /**
         * Creates a {@code Version} for the specified file.
         *
         * @param file file path
         * @param fileAttributes file attributes
         * @param version version number
         * @param hidden {@code true} if the version is hidden
         * @param comment version comment
         * @throws IOException if the content type cannot be determined
         */
        public Version(Path file, BasicFileAttributes fileAttributes, int version, boolean hidden, String comment, String creator) throws IOException {
            super(file, fileAttributes, false);
            this.version = version;
            this.hidden = hidden;
            this.comment = comment;
            this.creator = creator == null ? "" : creator;
        }

        /**
         * Gets the version number.
         *
         * @return version number
         */
        public int getVersion() {
            return version;
        }

        /**
         * Indicates whether this version is hidden.
         *
         * @return {@code true} if the version is hidden
         */
        public boolean isHidden() {
            return hidden;
        }

        /**
         * Gets the comment associated with this version.
         *
         * @return version comment
         */
        public String getComment() {
            return comment;
        }

        public String getCreator() {
            return creator;
        }
    }

    /**
     * Returns a debug representation of this version metadata object.
     *
     * @return human readable representation
     */
    @Override
    public String toString() {
        return "VersionInfo{" + "defaultVersion=" + defaultVersion + ", latestVersion=" + latestVersion + ", versions=" + versions + '}';
    }

    /**
     * Converts this instance into a map for convenient serialization.
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
