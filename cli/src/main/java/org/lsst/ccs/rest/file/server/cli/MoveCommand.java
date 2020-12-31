package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Simple cat command for use with rest server
 *
 * @author tonyj
 */
@Command(name = "mv", description = "Move/rename a file or directory")
public class MoveCommand implements Callable<Void> {

    @ParentCommand
    private TopLevelCommand parent;

    @Parameters(paramLabel = "<from>", description = "Source")
    private String from;

    @Parameters(paramLabel = "<to>", description = "Destination")
    private String to;

    @Override
    public Void call() throws IOException {
        try (FileSystem restfs = parent.createFileSystem()) {
            Path fromPath = restfs.getPath(from);
            Path toPath = restfs.getPath(to);
            Files.move(fromPath, toPath);
            return null;
        }
    }
}
