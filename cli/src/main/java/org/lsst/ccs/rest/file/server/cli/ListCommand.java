package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.lsst.ccs.rest.file.server.client.VersionedFileAttributes;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Simple ls command for use with rest server
 *
 * @author tonyj
 */
@Command(name = "ls", usageHelpAutoWidth = true, description = "List files on remove rest file server")
public class ListCommand implements Callable<Void> {

    @CommandLine.ParentCommand
    private TopLevelCommand parent;

    @Option(names = {"-l", "--long"}, description = "Detailed listing")
    private boolean showLong;

    @Option(names = {"-a", "--all"}, description = "Include hidden files")
    private boolean showAll;

    @Option(names = {"-h", "--human-readable"}, description = "with -l, print sizes in human readable format (e.g., 1K 234M 2G)")
    private boolean humanReadable;

    @Option(names = {"--si"}, description = "like -h, but use powers of 1000 not 1024")
    private boolean si;

    @Option(names = {"--color"}, description = "colorize the output", defaultValue = "true", negatable = true, showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private boolean colorize;

    @Option(names = {"--full-time"}, description = "Show full date format (with -l)")
    private boolean fullTime;

    @Parameters(paramLabel = "<path>", defaultValue = "/", description = "Path to list")
    private String path;

    @Override
    public Void call() throws IOException {
        try (FileSystem restfs = parent.createFileSystem()) {
            Path restPath = restfs.getPath(path);
            boolean isDirectory = Files.isDirectory(restPath);
            boolean isVersionedFile = (boolean) Files.getAttribute(restPath, "isVersionedFile");
            FileSizeFormatter fsf = new FileSizeFormatter(humanReadable, si);
            FileDateFormatter fdf = new FileDateFormatter(fullTime);
            Ansi ansi = colorize ? Ansi.AUTO : Ansi.OFF;
            if (isDirectory) {
                try (Stream<Path> directoryStream = Files.list(restPath)) {
                    directoryStream.forEach(p -> {
                        try {
                            BasicFileAttributes bfa = Files.readAttributes(p, BasicFileAttributes.class);
                            String color = bfa.isDirectory() ? "blue" : bfa.isOther() ? "green" : "white";
                            if (showLong || si) {
                                String line = String.format("@|%s %10s %s %s|@", color, fsf.format(bfa.size()), fdf.format(bfa.lastModifiedTime()), p.getFileName());
                                System.out.println(ansi.string(line));
                            } else {
                                String line = String.format("@|%s %s|@", color, p.getFileName());
                                System.out.println(ansi.string(line));
                            }
                        } catch (IOException x) {
                            System.out.println("IOException: " + p.getFileName());
                        }
                    });
                }
            } else if (isVersionedFile) {
                VersionedFileAttributes vfa = Files.readAttributes(restPath, VersionedFileAttributes.class);
                for (int version : vfa.getVersions()) {
                    if (!showAll && vfa.isHidden(version)) {
                        continue;
                    }
                    BasicFileAttributes bfa = vfa.getAttributes(version);
                    String color = version == vfa.getDefaultVersion() ? "blue" : version == vfa.getLatestVersion() ? "green" : "white";
                    List<String> attributes = new ArrayList<>();
                    if (version == vfa.getDefaultVersion()) {
                        attributes.add("default");
                    }
                    if (version == vfa.getLatestVersion()) {
                        attributes.add("latest");
                    }
                    if (vfa.isHidden(version)) {
                        attributes.add("hidden");
                    }
                    String info = String.join(",", attributes);
                    String comment = vfa.getComment(version);
                    if (showLong || si) {
                        String line = String.format("@|%s %10s %s %3d %s %s|@", color, fsf.format(bfa.size()), fdf.format(bfa.lastModifiedTime()), version, info, comment);
                        System.out.println(ansi.string(line));
                    } else {
                        String line = String.format("@|%s %3d %s|@", color, version, info);
                        System.out.println(ansi.string(line));
                    }
                }
            } else {
                throw new NotDirectoryException("Not a directory or versioned file: " + path);
            }
            return null;
        }
    }
}
