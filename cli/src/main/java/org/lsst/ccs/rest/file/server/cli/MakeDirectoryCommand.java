package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Simple mkdir command for use with rest server
 * @author tonyj
 */
@Command(name = "mkdir", description = "Create a new directory")
public class MakeDirectoryCommand implements Callable<Void> {
    
    @ParentCommand
    private TopLevelCommand parent;

    @Parameters(paramLabel="<path>", description = "Path to directory to create")    
    private String path;
    
    @CommandLine.Option(names = {"-p", "--parents"}, description = "no error if existing, make parent directories as needed")
    private boolean createParents;

    @Override   
    public Void call() throws IOException {
        FileSystem restfs = parent.createFileSystem();
        Path restPath = restfs.getPath(path);
        if (createParents) {
            Files.createDirectories(restPath); 
        } else {
            Files.createDirectory(restPath); 
        }
        return null;
    }
}
