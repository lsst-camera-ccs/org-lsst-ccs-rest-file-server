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
 * Represents metadata about a file or directory returned by the REST file
 * server.
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

    /**
     * Creates a {@code RestFileInfo} from JSON properties.
     *
     * @param lastModified last modification time in milliseconds
     * @param creationTime creation time in milliseconds
     * @param lastAccessTime last access time in milliseconds
     * @param size size of the file in bytes
     * @param mimeType detected MIME type of the file
     * @param name file name
     * @param fileKey unique file key
     * @param directory {@code true} if the path is a directory
     * @param other {@code true} if the path is some other type
     * @param regularFile {@code true} if the path is a regular file
     * @param symbolicLink {@code true} if the path is a symbolic link
     * @param versionedFile {@code true} if the path is a versioned file
     * @param children child entries if the path is a directory
     */
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
    
    /**
     * Copy constructor.
     *
     * @param other existing {@code RestFileInfo} to copy
     */
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
    
    /**
     * Constructs a {@code RestFileInfo} for the supplied file.
     *
     * @param file file path
     * @param fileAttributes file attributes
     * @param isVersionedFile {@code true} if the file is versioned
     * @throws IOException if the content type cannot be determined
     */
    public RestFileInfo(Path file, BasicFileAttributes fileAttributes, boolean isVersionedFile) throws IOException {
        this(file, fileAttributes, isVersionedFile, null);
    }

    /**
     * Constructs a {@code RestFileInfo} for the supplied file with child
     * entries.
     *
     * @param file file path
     * @param fileAttributes file attributes
     * @param isVersionedFile {@code true} if the file is versioned
     * @param children child file information
     * @throws IOException if the content type cannot be determined
     */
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

    /**
     * Gets the last modification time.
     *
     * @return last modification time in milliseconds since the epoch
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Gets the creation time.
     *
     * @return creation time in milliseconds since the epoch
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Gets the last access time.
     *
     * @return last access time in milliseconds since the epoch
     */
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Gets the size of the file.
     *
     * @return file size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets the detected MIME type of the file.
     *
     * @return MIME type, or {@code null} if unknown
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Gets the file name.
     *
     * @return file name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the unique file key.
     *
     * @return file key string
     */
    public String getFileKey() {
        return fileKey;
    }

    /**
     * Determines whether the path represents a directory.
     *
     * @return {@code true} if this is a directory and not a versioned file
     */
    public boolean isDirectory() {
        return isDirectory && !isVersionedFile;
    }

    /**
     * Determines whether the path represents an "other" file type.
     *
     * @return {@code true} if the path is some other type or a versioned file
     */
    public boolean isOther() {
        return isOther || isVersionedFile;
    }

    /**
     * Determines whether the path represents a regular file.
     *
     * @return {@code true} if this is a regular file
     */
    public boolean isRegularFile() {
        return isRegularFile;
    }

    /**
     * Determines whether the path represents a symbolic link.
     *
     * @return {@code true} if this is a symbolic link
     */
    public boolean isSymbolicLink() {
        return isSymbolicLink;
    }

    /**
     * Determines whether the path represents a versioned file.
     *
     * @return {@code true} if this is a versioned file
     */
    public boolean isVersionedFile() {
        return isVersionedFile;
    }

    /**
     * Gets the child entries of this file or directory.
     *
     * @return list of child file information, or {@code null} if none
     */
    public List<RestFileInfo> getChildren() {
        return children;
    }

    /**
     * Returns a string representation of this file description for logging or
     * debugging purposes.
     *
     * @return human readable representation
     */
    @Override
    public String toString() {
        return "RestFileInfo{" + "lastModified=" + lastModified + ", size=" + size + ", mimeType=" + mimeType + ", name=" + name + '}';
    }

    /**
     * Converts this instance to a map of simple values suitable for
     * serialization.
     *
     * @return map containing the file metadata
     */
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
