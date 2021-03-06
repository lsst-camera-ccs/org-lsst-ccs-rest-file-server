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
 * Information returned by the rest-file-server for a versioned file
 *
 * @author tonyj
 */
public class VersionInfo implements Serializable {

    private final int defaultVersion;
    private final int latestVersion;
    private final List<VersionInfo.Version> versions;

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

    public int getDefault() {
        return defaultVersion;
    }

    public int getLatest() {
        return latestVersion;
    }

    public List<Version> getVersions() {
        return versions;
    }

    public static class Version extends RestFileInfo {

        private final int version;

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
        
        public Version(Path file, BasicFileAttributes fileAttributes, int version) throws IOException {
            super(file, fileAttributes, false);
            this.version = version;
        }

        public Version(Version other) {
            super(other);
            this.version = other.version;
        }
        
        public Version(VersionInfoV2.Version v2) {
            super(v2);
            this.version = v2.getVersion();
        }

        public int getVersion() {
            return version;
        }
    }

    @Override
    public String toString() {
        return "VersionInfo{" + "defaultVersion=" + defaultVersion + ", latestVersion=" + latestVersion + ", versions=" + versions + '}';
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("latestVersion", getLatest());
        result.put("defaultVersion", getDefault());
        return result;
    }

}
