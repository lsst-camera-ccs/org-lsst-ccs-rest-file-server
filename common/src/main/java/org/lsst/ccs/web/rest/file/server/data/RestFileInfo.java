package org.lsst.ccs.web.rest.file.server.data;

import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information returned by the rest file server for a directory or file
 * @author tonyj
 */
public class RestFileInfo {
    private long lastModified;
    private long creationTime;
    private long lastAccessTime;
    private long size;
    private String mimeType;
    private String name;       
    private String fileKey;
    private boolean isDirectory;
    private boolean isOther;
    private boolean isRegularFile;
    private boolean isSymbolicLink;
    private boolean isVersionedFile;
    private List<RestFileInfo> children;

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public boolean isDirectory() {
        return isDirectory && !isVersionedFile;
    }

    public void setIsDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public boolean isOther() {
        return isOther || isVersionedFile;
    }

    public void setIsOther(boolean isOther) {
        this.isOther = isOther;
    }

    public boolean isRegularFile() {
        return isRegularFile;
    }

    public void setIsRegularFile(boolean isRegularFile) {
        this.isRegularFile = isRegularFile;
    }

    public boolean isSymbolicLink() {
        return isSymbolicLink;
    }

    public void setIsSymbolicLink(boolean isSymbolicLink) {
        this.isSymbolicLink = isSymbolicLink;
    }

    public boolean isVersionedFile() {
        return isVersionedFile;
    }

    public void setIsVersionedFile(boolean isVersionedFile) {
        this.isVersionedFile = isVersionedFile;
    }

    public List<RestFileInfo> getChildren() {
        return children;
    }

    public void setChildren(List<RestFileInfo> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return "RestFileInfo{" + "lastModified=" + lastModified + ", size=" + size + ", mimeType=" + mimeType + ", name=" + name + '}';
    }

    public Map<String, Object> getAsMap() {
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
