package org.lsst.ccs.web.rest.file.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

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
    private final Path path;

    VersionedFile(Path path) throws IOException {
        this.path = path;
        if (!isVersionedFile(path)) {
            throw new IOException("Not a versioned file: " + path);
        }
    }

    static boolean isVersionedFile(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return false;
        }
        UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        if (!view.list().contains(USER_VERSIONED_FILE)) {
            return false;
        }

        return Files.isSymbolicLink(path.resolve(LATEST)) && Files.isSymbolicLink(path.resolve(DEFAULT));
    }

    int[] getVersions() throws IOException {
        return Files.list(path)
                .filter(p -> !Files.isSymbolicLink(p))
                .map(p -> p.getFileName().toString())
                .filter(s -> s.matches(("\\d+")))
                .mapToInt(s -> Integer.parseInt(s))
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
        return path.resolve("default");
    }

    Path getLatest() {
        return path.resolve("latest");
    }

    int addVersion(byte[] content, boolean onlyIfChanged) throws IOException {
        int version = getLatestVersion() + 1;
        if (version > 1 && onlyIfChanged) {
            byte[] previousData = Files.readAllBytes(getLatest());
            if (Arrays.equals(previousData, content)) return getLatestVersion();
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
        return new VersionedFile(dir);
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
