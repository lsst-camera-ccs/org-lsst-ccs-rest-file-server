package org.lsst.ccs.web.rest.file.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Encapsulation of a versioned file. The current implementation stores the file
 * as a directory, containing files named 1,2,3....n, with symbolic links for
 * the latest and default version of the file.
 *
 * @author tonyj
 */
public class VersionedFile {

    private static final Set<PosixFilePermission> READ_ONLY = PosixFilePermissions.fromString("r--r--r--");
    private static final String LATEST = "latest";
    private static final String DEFAULT = "default";
    private static final String USER_VERSIONED_FILE = "user.isVersionedFile";
    private static final String META_FILE_NAME = "version-meta.properties";
    private static final String HIDDEN_VERSIONS_PROPERTY = "hidden-versions";
    private static final String COMMENT_PROPERTY = "comment.";


    private final Properties meta;
    private final Path path;

    /**
     * Creates a wrapper for an existing versioned file directory.
     *
     * @param path the directory representing the versioned file
     * @throws IOException if the path does not conform to the expected layout
     */
    VersionedFile(Path path) throws IOException {
        this.path = path;
        if (!isVersionedFile(path)) {
            throw new IOException("Not a versioned file: " + path);
        }
        // Should we cache this info, or read it each time we need it?
        meta = loadMetaFile(path);
    }

    /**
     * Checks whether the supplied path is a versioned file directory.
     *
     * @param path the directory to test
     * @return {@code true} if the directory contains version metadata
     * @throws IOException if an I/O error occurs
     */
    static boolean isVersionedFile(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return false;
        }
        boolean hasMetaFile = Files.exists(path.resolve(META_FILE_NAME));
        UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        // Relying on file attributes is a bad idea, since they are not maintaied by tools like rsync
        // So accept either file attribute or existance of meta-file 
        if (!view.list().contains(USER_VERSIONED_FILE) && !hasMetaFile) {
            return false;
        }
        if (!Files.isSymbolicLink(path.resolve(LATEST)) || !Files.isSymbolicLink(path.resolve(DEFAULT))) {
            return false;
        }
        // Old files did not have the meta-file, so create it here if missing
        if (!hasMetaFile) {
            createMetaFile(path);
        }
        return true;
    }

    /**
     * Returns all version numbers for this file including hidden versions.
     *
     * @return sorted array of version numbers
     * @throws IOException if the versions cannot be listed
     */
    int[] getVersions() throws IOException {
        return getVersions(true);
    }

    /**
     * Returns version numbers for this file.
     *
     * @param includeHidden whether hidden versions should be included
     * @return sorted array of version numbers
     * @throws IOException if the versions cannot be listed
     */
    int[] getVersions(boolean includeHidden) throws IOException {
        try (Stream<Path> list = Files.list(path)) {
            return list
                    .filter(p -> !Files.isSymbolicLink(p))
                    .map(p -> p.getFileName().toString())
                    .filter(s -> s.matches(("\\d+")))
                    .mapToInt(s -> Integer.parseInt(s))
                    .filter(v -> includeHidden || !isHidden(v))
                    .sorted()
                    .toArray();
        }
    }

    /**
     * Gets the most recent version number.
     *
     * @return the latest version
     * @throws IOException if the symbolic link cannot be resolved
     */
    int getLatestVersion() throws IOException {
        return Integer.parseInt(path.resolve(LATEST).toRealPath().getFileName().toString());
    }

    /**
     * Gets the version number that should be considered the default.
     *
     * @return the default version
     * @throws IOException if the symbolic link cannot be resolved
     */
    int getDefaultVersion() throws IOException {
        return Integer.parseInt(path.resolve(DEFAULT).toRealPath().getFileName().toString());
    }

    /**
     * Sets the version that should be considered the default.
     *
     * @param version the version number to mark as default
     * @throws IOException if the version is invalid or the link cannot be created
     */
    void setDefaultVersion(int version) throws IOException {
        final Path targetPath = path.resolve(String.valueOf(version));
        if (version < 1 || !Files.exists(targetPath)) {
            throw new IOException("Invalid version " + version);
        }
        Files.deleteIfExists(path.resolve(DEFAULT));
        Files.createSymbolicLink(path.resolve(DEFAULT), path.relativize(targetPath));
    }

    /**
     * Resolves the path for a specific version number.
     *
     * @param version the version to locate
     * @return the path to the version's file
     * @throws IOException if the version does not exist
     */
    Path getPathForVersion(int version) throws IOException {
        final Path targetPath = path.resolve(String.valueOf(version));
        if (!Files.exists(path)) {
            throw new IOException("Invalid version " + version);
        }
        return targetPath;
    }

    /**
     * Returns the symbolic link pointing to the default version file.
     *
     * @return path to the default version
     */
    Path getDefault() {
        return path.resolve(DEFAULT);
    }

    /**
     * Returns the symbolic link pointing to the latest version file.
     *
     * @return path to the latest version
     */
    Path getLatest() {
        return path.resolve(LATEST);
    }

    private Set<Integer> getHiddenVersions() {
        String hiddenVersionsString = meta.getProperty(HIDDEN_VERSIONS_PROPERTY, "");
        if (hiddenVersionsString.isEmpty()) {
            return new TreeSet<>();
        } else {
            return Arrays.stream(hiddenVersionsString.trim().split("\\s*,\\s*")).map(s -> Integer.valueOf(s)).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /**
     * Determines if the specified version is hidden.
     *
     * @param version version number to test
     * @return {@code true} if the version is hidden
     */
    boolean isHidden(int version) {
        return getHiddenVersions().contains(version);
    }
    

    /**
     * Marks a version as hidden or visible.
     *
     * @param version the version to modify
     * @param isHidden {@code true} to hide the version; {@code false} to unhide
     * @throws IOException if the version cannot be updated
     */
    void setHidden(int version, boolean isHidden) throws IOException {
        Set<Integer> hiddenVersions = getHiddenVersions();
        boolean modified;
        if (isHidden) {
            if (version == getLatestVersion()) {
                throw new RuntimeException("Latest version cannot be hidden");
            }
            if (version == getDefaultVersion()) {
                throw new RuntimeException("Default version cannot be hidden");
            }
            modified = hiddenVersions.add(version);
        } else {
            modified = hiddenVersions.remove(version);
        }
        if (modified) {
            meta.setProperty(HIDDEN_VERSIONS_PROPERTY, hiddenVersions.stream().map(String::valueOf).collect(Collectors.joining(",")));
            updateMetaFile(path, meta);
        }
    }
    
    /**
     * Retrieves the stored comment for a version.
     *
     * @param version the version whose comment is requested
     * @return the comment text or an empty string
     */
    String getComment(int version) {
        return meta.getProperty(COMMENT_PROPERTY + version, "");
    }

    /**
     * Updates the comment associated with a version.
     *
     * @param version the version to update
     * @param comment comment text to store
     * @throws IOException if the metadata file cannot be updated
     */
    void setComment(int version, String comment) throws IOException {
        meta.setProperty(COMMENT_PROPERTY + version, comment);
        updateMetaFile(path, meta);
    }

    /**
     * Adds a new version of the file using the supplied content.
     *
     * @param content file bytes for the new version
     * @param onlyIfChanged if {@code true}, identical content will not create a new version
     * @return the version number written
     * @throws IOException if the version cannot be written
     */
    int addVersion(byte[] content, boolean onlyIfChanged) throws IOException {
        int version = getLatestVersion() + 1;
        if (version > 1 && onlyIfChanged) {
            byte[] previousData = Files.readAllBytes(getLatest());
            if (Arrays.equals(previousData, content)) {
                return getLatestVersion();
            }
        }
        Path file = path.resolve(String.valueOf(version));
        Files.write(file, content);
        Files.setPosixFilePermissions(file, READ_ONLY);
        Files.deleteIfExists(path.resolve(LATEST));
        Files.createSymbolicLink(path.resolve(LATEST), path.relativize(file));
        return version;
    }

    /**
     * Creates a new versioned file at the given path with initial content.
     *
     * @param path directory to create for the versioned file
     * @param content initial file bytes
     * @return a {@code VersionedFile} representing the created file
     * @throws IOException if the file already exists or cannot be created
     */
    static VersionedFile create(Path path, byte[] content) throws IOException {
        if (Files.exists(path)) {
            throw new IOException("File already exists: " + path);
        }
        Path dir = Files.createDirectory(path);
        UserDefinedFileAttributeView view = Files.getFileAttributeView(dir, UserDefinedFileAttributeView.class);
        view.write(USER_VERSIONED_FILE, Charset.defaultCharset().encode("true"));
        Path file = dir.resolve("1");
        Files.write(file, content);
        Files.setPosixFilePermissions(file, READ_ONLY);
        Files.createSymbolicLink(dir.resolve(LATEST), dir.relativize(file));
        Files.createSymbolicLink(dir.resolve(DEFAULT), dir.relativize(file));
        createMetaFile(dir);
        return new VersionedFile(dir);
    }

    private static void createMetaFile(Path dir) throws IOException {
        Properties meta = new Properties();
        updateMetaFile(dir, meta);
    }

    private static void updateMetaFile(Path dir, Properties props) throws IOException {
        try (OutputStream out = Files.newOutputStream(dir.resolve(META_FILE_NAME))) {
            props.store(out, null);
        }
    }

    /**
     * Loads the version metadata file from the specified directory.
     *
     * @param dir directory containing the metadata file
     * @return loaded properties
     * @throws IOException if the metadata cannot be read
     */
    static Properties loadMetaFile(Path dir) throws IOException {
        try (final InputStream in = Files.newInputStream(dir.resolve(META_FILE_NAME))) {
            Properties meta = new Properties();
            meta.load(in);
            return meta;
        }
    }

    /**
     * Converts an existing unversioned file into a versioned file structure.
     *
     * @param unversionedFile the existing file to convert
     * @return a {@code VersionedFile} representing the converted file
     * @throws IOException if conversion fails
     */
    static VersionedFile convert(Path unversionedFile) throws IOException {
        if (!Files.exists(unversionedFile)) {
            throw new NoSuchFileException("File not found " + unversionedFile);
        }
        if (VersionedFile.isVersionedFile(unversionedFile)) {
            throw new IOException("File is already versioned: " + unversionedFile);
        }
        Path tempName = unversionedFile.resolveSibling(unversionedFile.getFileName() + "$$TMP$$");
        Path dir = Files.createDirectory(tempName);
        UserDefinedFileAttributeView view = Files.getFileAttributeView(dir, UserDefinedFileAttributeView.class);
        view.write(USER_VERSIONED_FILE, Charset.defaultCharset().encode("true"));
        Path newFileName = tempName.resolve("1");
        Files.move(unversionedFile, newFileName);
        Files.setPosixFilePermissions(newFileName, READ_ONLY);
        Files.createSymbolicLink(dir.resolve(LATEST), dir.relativize(newFileName));
        Files.createSymbolicLink(dir.resolve(DEFAULT), dir.relativize(newFileName));
        createMetaFile(dir);
        Files.move(dir, unversionedFile);
        return new VersionedFile(unversionedFile);
    }

    /**
     * Returns the base file name of the versioned file directory.
     *
     * @return the file name
     */
    String getFileName() {
        return path.getFileName().toString();
    }

    /**
     * Deletes the versioned file and all of its versions from disk.
     *
     * @throws IOException if deletion fails
     */
    void delete() throws IOException {
        Files.walk(path).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
