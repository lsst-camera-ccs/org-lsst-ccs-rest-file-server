package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information returned by the rest-file-server for a versioned file
 * @author tonyj
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionInfoV2 implements Serializable {

    private final int defaultVersion;
    private final int latestVersion;
    private final List<VersionInfoV2.Version> versions;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VersionInfoV2(@JsonProperty("default") int defaultVersion, @JsonProperty("latest") int latestVersion, @JsonProperty("versions") List<Version> versions) {
        this.defaultVersion = defaultVersion;
        this.latestVersion = latestVersion;
        this.versions = versions;
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
            @JsonProperty("comment") String comment) {

            super(lastModified, creationTime, lastAccessTime, size, mimeType, name, fileKey, directory, other, regularFile, symbolicLink, versionedFile, children);
            this.version = version;
            this.hidden = hidden;
            this.comment = comment == null ? "" : comment;
        }
        
        public Version(Path file, BasicFileAttributes fileAttributes, int version, boolean hidden, String comment) throws IOException {
            super(file, fileAttributes, false);
            this.version = version;
            this.hidden = hidden;
            this.comment = comment;
        }

        public int getVersion() {
            return version;
        }

        public boolean isHidden() {
            return hidden;
        }

        public String getComment() {
            return comment;
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
