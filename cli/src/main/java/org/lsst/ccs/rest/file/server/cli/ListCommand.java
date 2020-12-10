package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Simple ls command for use with rest server
 * @author tonyj
 */
@Command(name="ls", description = "List files on remove rest file server")
public class ListCommand implements Callable<Void> {

    @CommandLine.ParentCommand
    private TopLevelCommand parent;

    @Option(names = {"-l", "--long"}, description = "Detailed listing")
    private boolean showLong;

    @Parameters(paramLabel="<path>", defaultValue="/", description = "Path to list")    
    private String path;

    @Override
    public Void call() throws IOException {
        FileSystem restfs = parent.createFileSystem();
        Path restPath = restfs.getPath(path);
        try (Stream<Path> directoryStream = Files.list(restPath)) {
            if (showLong) {
                directoryStream.forEach(p -> {
                    try {
                        BasicFileAttributes bfa = Files.readAttributes(p, BasicFileAttributes.class);
                        System.out.printf("%d %s %s\n", bfa.size(), bfa.lastModifiedTime(), p.getFileName());
                    } catch (IOException x) {
                        System.out.println("IOException: "+p.getFileName());
                    }
                });
            } else {
                directoryStream.forEach(System.out::println);
            }
        }
        return null;
    }
}
