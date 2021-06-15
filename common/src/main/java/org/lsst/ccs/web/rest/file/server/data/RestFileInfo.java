package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information returned by the rest file server for a directory or file
 *
 * @author tonyj
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestFileInfo implements Serializable {

    private final long lastModified;
    private final long creationTime;
    private final long lastAccessTime;
    private final long size;
    private final String mimeType;
    private final String name;
    private final String fileKey;
    private final boolean isDirectory;
    private final boolean isOther;
    private final boolean isRegularFile;
    private final boolean isSymbolicLink;
    private final boolean isVersionedFile;
    private final List<RestFileInfo> children;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RestFileInfo(
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
            @JsonProperty("children") List<RestFileInfo> children) {
        this.lastModified = lastModified;
        this.creationTime = creationTime;
        this.lastAccessTime = lastAccessTime;
        this.size = size;
        this.mimeType = mimeType;
        this.name = name;
        this.fileKey = fileKey;
        this.isDirectory = directory;
        this.isOther = other;
        this.isRegularFile = regularFile;
        this.isSymbolicLink = symbolicLink;
        this.isVersionedFile = versionedFile;
        this.children = children;
    }
    
    public RestFileInfo(RestFileInfo other) {
        this.lastModified = other.lastModified;
        this.creationTime = other.creationTime;
        this.lastAccessTime = other.lastAccessTime;
        this.size = other.size;
        this.mimeType = other.mimeType;
        this.name = other.name;
        this.fileKey = other.fileKey;
        this.isDirectory = other.isDirectory;
        this.isOther = other.isOther;
        this.isRegularFile = other.isRegularFile;
        this.isSymbolicLink = other.isSymbolicLink;
        this.isVersionedFile = other.isVersionedFile;
        this.children = other.children;        
    }
    
    public RestFileInfo(Path file, BasicFileAttributes fileAttributes, boolean isVersionedFile) throws IOException {
        this(file, fileAttributes, isVersionedFile, null);
    }
    
    public RestFileInfo(Path file, BasicFileAttributes fileAttributes, boolean isVersionedFile, List<RestFileInfo> children) throws IOException {
        this.name = file.getFileName().toString();
        this.size = fileAttributes.size();
        this.lastModified = fileAttributes.lastModifiedTime().toMillis();
        this.fileKey = fileAttributes.fileKey().toString();
        this.isDirectory = fileAttributes.isDirectory();
        this.isOther = fileAttributes.isOther();
        this.isRegularFile = fileAttributes.isRegularFile();
        this.isSymbolicLink = fileAttributes.isSymbolicLink();
        this.lastAccessTime = fileAttributes.lastAccessTime().toMillis();
        this.creationTime = fileAttributes.creationTime().toMillis();
        this.mimeType = Files.probeContentType(file);
        this.isVersionedFile = isVersionedFile;
        this.children = children;
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getSize() {
        return size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getName() {
        return name;
    }

    public String getFileKey() {
        return fileKey;
    }

    public boolean isDirectory() {
        return isDirectory && !isVersionedFile;
    }

    public boolean isOther() {
        return isOther || isVersionedFile;
    }

    public boolean isRegularFile() {
        return isRegularFile;
    }

    public boolean isSymbolicLink() {
        return isSymbolicLink;
    }

    public boolean isVersionedFile() {
        return isVersionedFile;
    }

    public List<RestFileInfo> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return "RestFileInfo{" + "lastModified=" + lastModified + ", size=" + size + ", mimeType=" + mimeType + ", name=" + name + '}';
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("lastAccessTime", FileTime.fromMillis(lastAccessTime));
        result.put("lastModifiedTime", FileTime.fromMillis(lastModified));
        result.put("size", getSize());
        result.put("creationTime", FileTime.fromMillis(creationTime));
        result.put("isSymbolicLink", isSymbolicLink());
        result.put("isRegularFile", isRegularFile());
        result.put("fileKey", getFileKey());
        result.put("isOther", isOther());
        result.put("isDirectory", isDirectory());
        result.put("isVersionedFile", isVersionedFile());
        return result;
    }

}
