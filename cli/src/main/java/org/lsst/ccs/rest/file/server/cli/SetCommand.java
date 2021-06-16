package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributeView;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Simple cat command for use with rest server
 * @author tonyj
 */
@Command(name = "set", description = "Modify settings on a versioned file")
public class SetCommand implements Callable<Void> {
    
    @ParentCommand
    private TopLevelCommand parent;

    @Parameters(paramLabel="<path>", description = "Path to file")    
    private String path;
    
    @CommandLine.Option(names = {"-v", "--version"}, description = "The version to use. ", defaultValue = "default", 
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String version;

    @CommandLine.Option(names = {"-d", "--default"}, description = "Make this the default version")
    private boolean makeDefault;

    @CommandLine.Option(names = {"-c", "--comment"}, description = "Comment for specified version")
    private String comment;

    @CommandLine.Option(names = {"-h", "--hidden"}, description = "Hide specified version")
    private Boolean hidden;
    
    @Spec CommandSpec spec;

    @Override   
    public Void call() throws IOException {
        try (FileSystem restfs = parent.createFileSystem()) {
            Path restPath = restfs.getPath(path);
            boolean isVersionedFile = (boolean) Files.getAttribute(restPath, "isVersionedFile");
            if (!isVersionedFile) throw new IllegalArgumentException(("Not a versioned file"));
            VersionedFileAttributeView versionView = Files.getFileAttributeView(restPath, VersionedFileAttributeView.class);
            VersionedFileAttributes attributes = versionView.readAttributes();
            VersionOpenOption voo = VersionOpenOption.of(version);
            int intVersion = voo.getIntVersion(attributes);
            if (makeDefault) {
                versionView.setDefaultVersion(intVersion);
            }
            if (comment != null) {
                versionView.setComment(intVersion, comment);
            }
            if (hidden != null) {
                versionView.setHidden(intVersion, hidden);
            }
            return null;
        }
    }
}
