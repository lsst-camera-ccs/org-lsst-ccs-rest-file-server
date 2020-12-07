package org.lsst.ccs.rest.file.server.client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 *
 * @author tonyj
 */
public class Main {

    public static void main(String[] args) throws IOException {
        Map<String, Object> env = new HashMap<>();
        URI uri = URI.create("ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/");
        FileSystem restfs = FileSystems.newFileSystem(uri, env);
        System.out.println("I got "+restfs);
        Path pathInRestServer = restfs.getPath("image-handling-dc01.app");
        System.out.println("I got "+pathInRestServer);
        System.out.println(Files.exists(pathInRestServer));
        System.out.println(Files.size(pathInRestServer));
        System.out.println(Files.getLastModifiedTime(pathInRestServer));

        List<String> readAllLines = Files.readAllLines(pathInRestServer);
//        for (String line : readAllLines) {
//            System.out.println(line);
//        }
        System.out.printf("Read %d lines\n", readAllLines.size());
//        Path externalTxtFile = Paths.get("/home/tonyj/Documents/CTM_Notes.txt");
//        Files.copy( externalTxtFile,pathInRestServer,
//                    StandardCopyOption.REPLACE_EXISTING );
        Path root = restfs.getRootDirectories().iterator().next();
        System.out.println(Files.isDirectory(root));
        Stream<Path> stream = Files.list(root);
        stream.forEach(path -> System.out.println(path.getFileName().toString()));
        
        BasicFileAttributeView fileAttributeView = Files.getFileAttributeView(pathInRestServer, BasicFileAttributeView.class);
        
        //Map<String, Object> readAttributes = Files.readAttributes(pathInRestServer, "*");
    }

}
