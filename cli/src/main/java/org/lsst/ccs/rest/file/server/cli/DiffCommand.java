package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.lsst.ccs.rest.file.server.cli.Utils.OpenOptionsBuilder;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionedOpenOption;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Command that displays differences between two versions of a file on the
 * REST file server.
 *
 * @author tonyj
 */
@Command(name = "diff", description = "Show diffs for versioned file")
public class DiffCommand implements Callable<Void> {

    @ParentCommand
    private TopLevelCommand parent;

    @Parameters(paramLabel = "<path>", description = "Path to file to diff")
    private String path;

    @CommandLine.Option(names = {"-v", "--version"}, description = "The version to diff. By default the latest", defaultValue = "latest",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String v1;

    @CommandLine.Option(names = {"-v2"}, description = "The version to diff against",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String v2;

    @Spec
    CommandSpec spec;

    @Override
    /**
     * Executes the diff command.
     *
     * @return {@code null} always
     * @throws IOException if an I/O error occurs while generating the diff
     */
    public Void call() throws IOException {
        try (FileSystem restfs = parent.createFileSystem()) {
            Path restPath = restfs.getPath(path);
            boolean isVersionedFile = (boolean) Files.getAttribute(restPath, "isVersionedFile");
            if (!isVersionedFile) {
                throw new IllegalArgumentException(("Not a versioned file"));
            }
            OpenOptionsBuilder builder = Utils.openOptionsBuilder();
            builder.add(VersionedOpenOption.DIFF);
            if (v1 != null) {
                builder.add(VersionOpenOption.of(v1));
            }
            if (v2 != null) {
                builder.add(VersionOpenOption.of(v2));
            }
            try (InputStream in = Files.newInputStream(restPath, builder.build())) {
                // Java 9 in.transferTo(System.out);
                Utils.copy(in, System.out);
            }
            return null;
        }
    }
}
