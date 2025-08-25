package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.lsst.ccs.rest.file.server.cli.Utils.OpenOptionsBuilder;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Command that reads or writes a file on the REST file server.
 * <p>
 * When run without options the file identified by {@code path} is written to
 * standard output. With {@code --create} or {@code --versioned} the command
 * reads from standard input to create a new file.
 *
 * @author tonyj
 */
@Command(name = "cat", description = "Write the contents of a file to standard output")
public class CatCommand implements Callable<Void> {
    
    @ParentCommand
    private TopLevelCommand parent;

    @Parameters(paramLabel="<path>", description = "Path to file to cat")    
    private String path;
    
    @CommandLine.Option(names = {"-v", "--version"}, description = "If a versioned file, the version to cat. ", defaultValue = "default", 
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String version;

    @CommandLine.Option(names = {"-c", "--create"}, description = "Create a new file by reading from stdin")
    private boolean create;

    @CommandLine.Option(names = {"-n", "--versioned"}, description = "Create a new versioned file by reading from stdin")
    private boolean createVersioned;
    
    @Spec CommandSpec spec;

    @Override
    /**
     * Executes the cat command.
     *
     * @return {@code null} always
     * @throws IOException if an I/O error occurs while reading or writing the
     *         file
     */
    public Void call() throws IOException {
        try (FileSystem restfs = parent.createFileSystem()) {
            Path restPath = restfs.getPath(path);
            if (!create && !createVersioned) {
                OpenOptionsBuilder builder = Utils.openOptionsBuilder();
                boolean isVersionedFile = (boolean) Files.getAttribute(restPath, "isVersionedFile");
                if (isVersionedFile) builder.add(VersionOpenOption.of(version));
                try (InputStream in = Files.newInputStream(restPath, builder.build())) {
                    // Java 9 in.transferTo(System.out);
                    Utils.copy(in, System.out);
                }
            } else {
                OpenOptionsBuilder builder = Utils.openOptionsBuilder();
                if (createVersioned) builder.add(VersionOpenOption.LATEST);
                try (OutputStream out = Files.newOutputStream(restPath, builder.build())) {
                    Utils.copy(System.in, out);
                }
            }
            return null;
        }
    }
}
