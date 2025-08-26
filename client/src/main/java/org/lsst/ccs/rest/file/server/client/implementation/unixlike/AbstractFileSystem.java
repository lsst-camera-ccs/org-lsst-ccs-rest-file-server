package org.lsst.ccs.rest.file.server.client.implementation.unixlike;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Minimal {@link FileSystem} implementation providing Unix-like behaviour for
 * use by the REST client.
 */
public abstract class AbstractFileSystem extends FileSystem {

    private boolean isOpen = true;
    private final boolean isReadOnly = false;
    private final AbstractPath root = (AbstractPath) this.getPath("/");
    
    @Override
    public void close() throws IOException {
        isOpen = false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return isOpen;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /** {@inheritDoc} */
    @Override
    public String getSeparator() {
        return "/";
    }

    /** {@inheritDoc} */
    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(root);
    }
}
