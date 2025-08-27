package org.lsst.ccs.rest.file.server.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command that opens a remote file in an editor and writes back any
 * modifications.
 *
 * @author tonyj
 */
@Command(name = "edit", description = "Edit a file on the rest file server")
public class EditCommand implements Callable<Void> {

    @CommandLine.ParentCommand
    private TopLevelCommand parent;

    @Option(names = {"-e", "--editor"}, description = "Editor command to use, defaults to $EDITOR if defined, otherwise vi")
    private String editor;

    @Option(names = {"-v", "--version"}, description = "If a versioned file, the version to edit", defaultValue = "latest", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String version;

    @Parameters(paramLabel = "<path>", description = "Path to edit")
    private String path;

    @Override
    /**
     * Executes the edit command.
     *
     * @return {@code null} always
     * @throws Exception if an error occurs while editing or writing the file
     */
    public Void call() throws Exception {
        try (FileSystem restfs = parent.createFileSystem()) {
            Path restPath = restfs.getPath(path);
            boolean isVersionedFile = (boolean) Files.getAttribute(restPath, "isVersionedFile");
            Path tempPath = Files.createTempFile("rest-file-server", "edit");
            OpenOption[] options = isVersionedFile ? new OpenOption[]{VersionOpenOption.of(version)} : new OpenOption[0];
            try (InputStream in = Files.newInputStream(restPath, options)) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (invokeEditor(tempPath)) {
                if (isVersionedFile) {
                    try (OutputStream out = Files.newOutputStream(restPath)) {
                        Files.copy(tempPath, out);
                    }
                    int latestVersion = (int) Files.getAttribute(restPath, "latestVersion");
                    System.out.printf("New version (%d) was created\n", latestVersion);
                } else {
                    Files.copy(tempPath, restPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("File was updated");
                }
            }
            return null;
        }
    }

    private boolean invokeEditor(Path tempPath) throws IOException, InterruptedException {
        FileTime originalModificationTime = Files.getLastModifiedTime(tempPath);
        Checksum originalChecksum = computeChecksum(tempPath);
        ProcessBuilder builder = new ProcessBuilder();
        String editorCommand = editor != null ? editor : System.getenv("EDITOR") == null ? "vi" : System.getenv("EDITOR");
        List<String> command = new ArrayList<>(Arrays.asList(editorCommand.split("\\s+")));
        command.add(tempPath.toString());
        builder.command(command);
        builder.inheritIO();
        builder.redirectErrorStream(true);
        Process editorProcess = builder.start();
        try (BufferedReader errorStreamReader = new BufferedReader(new InputStreamReader(editorProcess.getInputStream()))) {
            for (;;) {
                String line = errorStreamReader.readLine();
                if (line == null) {
                    break;
                }
                System.out.println(line);
            }
        }
        int rc = editorProcess.waitFor();
        if (rc != 0) {
            throw new IOException("Editor exited with rc=" + rc);
        } else {
            return Files.getLastModifiedTime(tempPath).compareTo(originalModificationTime) > 0
                    && computeChecksum(tempPath).getValue() != originalChecksum.getValue();
        }
    }

    private Checksum computeChecksum(Path tempPath) throws IOException {
        // Java 11: OutputStream.nullOutputStream()
        OutputStream nullOutputStream = new OutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
            }
        };
        CheckedOutputStream checkedOutputStream = new CheckedOutputStream(nullOutputStream, new CRC32());
        Files.copy(tempPath, checkedOutputStream);
        return checkedOutputStream.getChecksum();
    }
}
