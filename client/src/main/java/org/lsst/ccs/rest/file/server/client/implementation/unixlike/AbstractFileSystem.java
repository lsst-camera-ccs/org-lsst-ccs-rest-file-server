package org.lsst.ccs.rest.file.server.client.implementation.unixlike;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;

/**
 *
 * @author tonyj
 */
public abstract class AbstractFileSystem extends FileSystem {

    private boolean isOpen = true;
    private final boolean isReadOnly = false;
    private final AbstractPath root = (AbstractPath) this.getPath("/");
    
    @Override
    public void close() throws IOException {
        isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(root);
    }
}
