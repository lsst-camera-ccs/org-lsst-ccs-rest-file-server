package org.lsst.ccs.rest.file.server.client.implementation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lsst.ccs.rest.file.server.client.VersionOpenOption;

/**
 *
 * @author tonyj
 */
public class VersionedPathCheck {
    
    private static final Pattern VERSIONED_FILE_PATTERN = Pattern.compile("(?<filename>.*)([(]{1}?(((?<version>[a-zA-Z0-9]*)))[)]{1})(\\.(?<extension>[a-zA-Z]*))?");
    protected String version = null;

    private final String pathWithVersionRemoved;

    public VersionedPathCheck(String path) {
        
        Matcher m = VERSIONED_FILE_PATTERN.matcher(path);        
        if ( m.matches() ) {
            path = m.group("filename");
            version = VersionOpenOption.of(m.group("version")).value();
            String extension = m.group("extension");
            if ( extension != null && !extension.isEmpty() ) {
                path += "."+extension;
            }
        }
        this.pathWithVersionRemoved = path;
    }

    public String getPathWithVersionRemoved() {
        return pathWithVersionRemoved;
    }

    public String getVersion() {
        return version;
    }
    
}
