package org.lsst.ccs.rest.file.server.client.implementation;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;

/**
 *
 * @author tonyj
 */
public class VersionedPathCheck {

    private static final Logger LOG = Logger.getLogger(VersionedPathCheck.class.getName());
    
    private static final Pattern VERSIONED_FILE_PATTERN = Pattern.compile("(?<filename>.*)([(]{1}?(((?<version>[a-zA-Z0-9]*)))[)]{1})(\\.(?<extension>[a-zA-Z]*))?");
    protected String version = null;

    private final String pathWithVersionRemoved;
    private final String originalPath;

    public VersionedPathCheck(String inputPath) {
        
        this.originalPath = inputPath;
        Matcher m = VERSIONED_FILE_PATTERN.matcher(inputPath);
        String path = inputPath;
        if ( m.matches() ) {
            path = m.group("filename");
            version = VersionOpenOption.of(m.group("version")).value();
            String extension = m.group("extension");
            if ( extension != null && !extension.isEmpty() ) {
                path += "."+extension;
            }
        }
        this.pathWithVersionRemoved = path;
        LOG.log(Level.INFO, "Input path {0} path with version removed {1} version {2}", new Object[]{originalPath, pathWithVersionRemoved, version});
    }

    public String getOriginalPath() {
       return originalPath;
    }
    
    public String getPathWithVersionRemoved() {
        return pathWithVersionRemoved;
    }

    public String getVersion() {
        return version;
    }
    
}
