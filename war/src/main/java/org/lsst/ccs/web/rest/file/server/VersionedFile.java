package org.lsst.ccs.web.rest.file.server;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Encapsulation of a versioned file. The current implementation stores the 
 * file as a directory, containing files named 1,2,3....n, with symbolic links 
 * for the latest and default version of the file.
 * @author tonyj
 */
public class VersionedFile {

    private static final String LATEST = "latest";
    private static final String DEFAULT = "default";
    private static final String USER_VERSIONED_FILE = "user.isVersionedFile";
    private final Path path;

    VersionedFile(Path path) throws IOException {
        this.path = path;
        if (!isVersionedFile(path)) {
            throw new IOException("Not a versioned file: "+path);
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
        return Files.list(path).filter(p-> !Files.isSymbolicLink(p)).mapToInt(p -> Integer.parseInt(p.getFileName().toString())).sorted().toArray();
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
            throw new IOException("Invalid version "+version);
        }
        Files.deleteIfExists(path.resolve(DEFAULT));
        Files.createSymbolicLink(path.resolve(DEFAULT), targetPath);
    }

    Path getPathForVersion(int version) throws IOException {
        final Path targetPath = path.resolve(String.valueOf(version));
        if (!Files.exists(path)) {
           throw new IOException("Invalid version "+version);
        }
        return targetPath;
    }

    Path getDefault() {
        return path.resolve("default");
    }

    Path getLatest() {
        return path.resolve("latest");
    }

    int addVersion(byte[] content) throws IOException {
        int version = getLatestVersion() + 1;
        Path file = path.resolve(String.valueOf(version));
        Files.write(file, content);
        Set<PosixFilePermission> readOnly = PosixFilePermissions.fromString("r--r--r--");
        Files.setPosixFilePermissions(file, readOnly);
        Files.deleteIfExists(path.resolve(LATEST));
        Files.createSymbolicLink(path.resolve(LATEST), file);
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
        Set<PosixFilePermission> readOnly = PosixFilePermissions.fromString("r--r--r--");
        Files.write(file, content);
        Files.setPosixFilePermissions(file, readOnly);
        Files.createSymbolicLink(dir.resolve(LATEST), file);
        Files.createSymbolicLink(dir.resolve(DEFAULT), file);
        return new VersionedFile(dir);
    }

    public static void main(String[] args) throws IOException, DiffException {
        Path tempDir = Files.createTempDirectory("versions");
        //Path tempDir = Paths.get("/home/tonyj/Data/testVersions");
        VersionedFile vf = VersionedFile.create(tempDir.resolve("test.file"), "Just Testing".getBytes());
        System.out.println(vf.getDefaultVersion());        
        System.out.println(vf.getLatestVersion());   
        Path file = vf.getDefault();
        String content = new String(Files.readAllBytes(file));
        System.out.println(content);   
        int nv = vf.addVersion("Just Testing again".getBytes());
        System.out.println(nv);
        System.out.println(vf.getDefaultVersion());        
        System.out.println(vf.getLatestVersion());    
        int[] versions = vf.getVersions();
        System.out.println(Arrays.toString(versions));
        vf.setDefaultVersion(2);
        System.out.println(vf.getDefaultVersion());       
        System.out.println(vf.getDefault());
        
        Path file1 = vf.getPathForVersion(1);
        Path file2 = vf.getPathForVersion(2);
        List<String> lines1 = Files.readAllLines(file1);
        List<String> lines2 = Files.readAllLines(file2);
        Patch<String> diff = DiffUtils.diff(lines1, lines2);
        System.out.println(diff);
        List<String> generateUnifiedDiff = UnifiedDiffUtils.generateUnifiedDiff("version1", "version2", lines1, diff, 2);
        for (String dLines : generateUnifiedDiff) {
            System.out.println(dLines);
        }
    }

    String getFileName() {
        return path.getFileName().toString();
    }
}
