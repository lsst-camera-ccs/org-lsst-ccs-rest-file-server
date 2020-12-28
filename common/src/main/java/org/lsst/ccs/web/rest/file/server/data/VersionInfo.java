package org.lsst.ccs.web.rest.file.server.data;

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
public class VersionInfo implements Serializable {

    private int defaultVersion;
    private int latestVersion;
    private List<VersionInfo.Version> versions;

    public int getDefault() {
        return defaultVersion;
    }

    public void setDefault(int defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public int getLatest() {
        return latestVersion;
    }

    public void setLatest(int latestVersion) {
        this.latestVersion = latestVersion;
    }

    public List<Version> getVersions() {
        return versions;
    }

    public void setVersions(List<Version> versions) {
        this.versions = versions;
    }
    
    public static class Version extends RestFileInfo {
        private int version;
        
        public Version() {
           super(); 
        }
        
        public Version(Path file, BasicFileAttributes fileAttributes) throws IOException {
            super(file, fileAttributes, false);
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
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
