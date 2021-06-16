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

    VersionedFile(Path path) throws IOException {
        this.path = path;
        if (!isVersionedFile(path)) {
            throw new IOException("Not a versioned file: " + path);
        }
        // Should we cache this info, or read it each time we need it?
        meta = loadMetaFile(path);
    }

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

    int[] getVersions() throws IOException {
        return getVersions(true);
    }

    int[] getVersions(boolean includeHidden) throws IOException {
        return Files.list(path)
                .filter(p -> !Files.isSymbolicLink(p))
                .map(p -> p.getFileName().toString())
                .filter(s -> s.matches(("\\d+")))
                .mapToInt(s -> Integer.parseInt(s))
                .filter(v -> includeHidden || !isHidden(v))
                .sorted()
                .toArray();
    }

    int getLatestVersion() throws IOException {
        return Integer.parseInt(path.resolve(LATEST).toRealPath().getFileName().toString());
    }

    int getDefaultVersion() throws IOException {
        return Integer.parseInt(path.resolve(DEFAULT).toRealPath().getFileName().toString());
    }

    void setDefaultVersion(int version) throws IOException {
        final Path targetPath = path.resolve(String.valueOf(version));
        if (version < 1 || !Files.exists(targetPath)) {
            throw new IOException("Invalid version " + version);
        }
        Files.deleteIfExists(path.resolve(DEFAULT));
        Files.createSymbolicLink(path.resolve(DEFAULT), path.relativize(targetPath));
    }

    Path getPathForVersion(int version) throws IOException {
        final Path targetPath = path.resolve(String.valueOf(version));
        if (!Files.exists(path)) {
            throw new IOException("Invalid version " + version);
        }
        return targetPath;
    }

    Path getDefault() {
        return path.resolve(DEFAULT);
    }

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

    boolean isHidden(int version) {
        return getHiddenVersions().contains(version);
    }
    

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
    
    String getComment(int version) {
        return meta.getProperty(COMMENT_PROPERTY+version, "");
    }
    

    void setComment(int version, String comment) throws IOException {
        meta.setProperty(COMMENT_PROPERTY+version, comment);
        updateMetaFile(path, meta);
    }
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

    static Properties loadMetaFile(Path dir) throws IOException {
        try (final InputStream in = Files.newInputStream(dir.resolve(META_FILE_NAME))) {
            Properties meta = new Properties();
            meta.load(in);
            return meta;
        }
    }

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

    String getFileName() {
        return path.getFileName().toString();
    }

    void delete() throws IOException {
        Files.walk(path).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
