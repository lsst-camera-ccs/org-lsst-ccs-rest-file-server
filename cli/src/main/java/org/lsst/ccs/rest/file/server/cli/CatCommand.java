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
 * @author tonyj
 */
@Command(name = "cat", description = "Write the contents of a file to standard output")
public class CatCommand implements Callable<Void> {
    
    @ParentCommand
    private TopLevelCommand parent;

    @Parameters(paramLabel="<path>", description = "Path to file to cat")    
    private String path;

    @Override   
    public Void call() throws IOException {
        FileSystem restfs = parent.createFileSystem();
        Path restPath = restfs.getPath(path);
        Files.copy(restPath, System.out);
        return null;
    }
}
