package org.lsst.ccs.rest.file.server.cli;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.HashMap;
import org.lsst.ccs.rest.file.server.client.RestFileSystemOptions;
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
@Command(name = "rfs", usageHelpAutoWidth = true, subcommands = {
    CatCommand.class,
    EditCommand.class,
    ListCommand.class, 
    MoveCommand.class,
    MakeDirectoryCommand.class,
    HelpCommand.class})
public class TopLevelCommand {

    @CommandLine.Option(names = {"-r", "--rest-server"}, defaultValue = "ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/",
            description = "Rest file server to connect to", showDefaultValue = ALWAYS)
    private String restServer;
    
    @Option(names = "--help", usageHelp = true, description = "display this help and exit")
    private boolean help;

    @Option(names = "--cacheOptions", description = "Caching options for file system")
    private RestFileSystemOptions.CacheOptions cacheOptions;

    @Option(names = "--cacheFallback", description = "Caching fallback options for file system")
    private RestFileSystemOptions.CacheFallback cacheFallback;
    
    @Option(names = "--cacheDir", description = "Directory to use for disk cache")
    private File cacheLocation;

    @Option(names = "--cacheLog", description = "Log caching operations")
    private boolean logCaching;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new TopLevelCommand()).execute(args);
        System.exit(exitCode);
    }

    FileSystem createFileSystem() throws IOException {
        URI uri = URI.create(restServer);
        HashMap<String, Object> options = new HashMap<>();
        if (cacheOptions != null) {
            options.put(RestFileSystemOptions.CACHE_OPTIONS, cacheOptions);
        }
        if (cacheFallback != null) {
            options.put(RestFileSystemOptions.CACHE_FALLBACK, cacheFallback);
        }
        if (cacheLocation != null) {
            options.put(RestFileSystemOptions.CACHE_LOCATION, cacheLocation);
        }
        options.put(RestFileSystemOptions.CACHE_LOGGING, logCaching);
        return FileSystems.newFileSystem(uri, options);
    }

}
