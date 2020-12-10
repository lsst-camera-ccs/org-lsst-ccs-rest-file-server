package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Collections;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import static picocli.CommandLine.Help.Visibility.ALWAYS;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

/**
 * Top level command for running all other commands
 *
 * @author tonyj
 */
@Command(name = "rfs", subcommands = {
    CatCommand.class,
    EditCommand.class,
    ListCommand.class, 
    HelpCommand.class})
public class TopLevelCommand {

    @CommandLine.Option(names = {"-r", "--rest-server"}, defaultValue = "ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/",
            description = "Rest file server to connect to", showDefaultValue = ALWAYS)
    private String restServer;
    
    @Option(names = "--help", usageHelp = true, description = "display this help and exit")
    private boolean help;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TopLevelCommand()).execute(args);
        System.exit(exitCode);
    }

    FileSystem createFileSystem() throws IOException {
        URI uri = URI.create(restServer);
        return FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
    }

}
