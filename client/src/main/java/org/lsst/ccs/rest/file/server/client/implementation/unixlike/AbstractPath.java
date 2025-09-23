package org.lsst.ccs.rest.file.server.client.implementation.unixlike;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An implementation of {@link Path} that works for any Unix-like file system
 * with
 * <ul>
 * <li>A single root
 * <li>Unix like file semantics
 * </ul>
 */
public abstract class AbstractPath implements Path {

    private final static String DELIMETER = "/";
    private final static LinkedList<String> EMPTY_PATH = new LinkedList<>();
    private final boolean isAbsolute;
    private final LinkedList<String> path;
    private final AbstractPathBuilder builder;

    /**
     * Creates a path from a string representation.
     *
     * @param builder factory used to create additional path instances
     * @param path textual path representation
     */
    protected AbstractPath(AbstractPathBuilder builder, String path) {
        this.builder = builder;
        this.isAbsolute = path.startsWith(DELIMETER);
        if (this.isAbsolute) {
            path = path.substring(1);
        }
        // TODO: check for illegal characters
        this.path = path.isEmpty() ? EMPTY_PATH : new LinkedList<>(Arrays.asList(path.split(DELIMETER + "+")));
    }

    /**
     * Creates a path from its components.
     *
     * @param builder factory used to create additional path instances
     * @param isAbsolute whether the path is absolute
     * @param path individual elements of the path
     */
    protected AbstractPath(AbstractPathBuilder builder, boolean isAbsolute, List<String> path) {
        this.builder = builder;
        this.isAbsolute = isAbsolute;
        this.path = path instanceof LinkedList ? (LinkedList<String>) path : new LinkedList<>(path);
    }

    @Override
    public boolean isAbsolute() {
        return isAbsolute;
    }

    @Override
    public Path getRoot() {
        return builder.getPath(true, EMPTY_PATH);
    }

    @Override
    public Path getFileName() {
        return path.isEmpty() ? null : builder.getPath(path.getLast());
    }

    @Override
    public Path getParent() {
        if (path.isEmpty()) {
            return null;
        } else {
            return builder.getPath(isAbsolute, path.subList(0, path.size()-1));
        }
    }

    @Override
    public int getNameCount() {
        return path.size();
    }

    @Override
    public Path getName(int index) {
        return builder.getPath(false, path.subList(index, index + 1));
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        return builder.getPath(false, path.subList(beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(Path other) {
        return startsWith(other.toString());
    }

    @Override
    public boolean startsWith(String other) {
        return this.toString().startsWith(other);
    }

    @Override
    public boolean endsWith(Path other) {
        return this.toString().endsWith(other.toString());
    }

    @Override
    public boolean endsWith(String other) {
        return this.toString().endsWith(other);
    }

    @Override
    public Path normalize() {
        List<String> newPath = new LinkedList<>(this.path);
        String previous, element;
        for (ListIterator<String> i = newPath.listIterator(); i.hasNext();) {
            previous = i.hasPrevious() ? newPath.get(i.previousIndex()) : null;
            element = i.next();
            if (".".equals(element) || element.isEmpty()) {
                i.remove();
            } else if ("..".equals(element) && previous!=null && !"..".equals(previous)) {
                i.remove();
                i.previous();
                i.remove();
            }
        }
        return builder.getPath(isAbsolute, newPath);
    }

    @Override
    public Path resolve(Path other) {
        if (other.isAbsolute()) {
            return other;
        }
        if (other.getNameCount() == 0) {
            return this;
        }
        List<String> newPath = new LinkedList<>(this.path);
        for (int i = 0; i < other.getNameCount(); i++) {
            newPath.add(other.getName(i).toString());
        }
        return builder.getPath(isAbsolute, newPath);
    }

    @Override
    public Path resolve(String other) {
        return resolve(builder.getPath(other));
    }

    @Override
    public Path resolveSibling(Path other) {
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(builder.getPath(other));
    }

    @Override
    public Path relativize(Path other) {
        if (!Objects.equals(other.getFileSystem(), this.getFileSystem())) {
            throw new IllegalArgumentException("Incompatible file system");
        }
        AbstractPath otherAbsolute = (AbstractPath) other.toAbsolutePath();
        AbstractPath thisAbsolute = (AbstractPath) this.toAbsolutePath();
        int commonRootDepth = 0;
        for (int i = 0; i < Math.min(thisAbsolute.path.size(), otherAbsolute.path.size()); i++) {
            if (!thisAbsolute.path.get(i).equals(otherAbsolute.path.get(i))) {
                break;
            }
            commonRootDepth++;
        }
        List<String> relativePath = new LinkedList<>();
        for (int i = commonRootDepth; i < thisAbsolute.path.size(); i++) {
            relativePath.add("..");
        }
        relativePath.addAll(otherAbsolute.path.subList(commonRootDepth, otherAbsolute.path.size()));
        return builder.getPath(false, relativePath);
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute) {
            return this;
        } else {
            // TODO: Support current directory?
            return this.getRoot().resolve(this);
        }
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return toAbsolutePath();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Watch service not supported yet.");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("Watch service not supported yet.");
    }

    @Override
    public Iterator<Path> iterator() {
        return new Iterator<Path>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return (i < getNameCount());
            }

            @Override
            public Path next() {
                if (i < getNameCount()) {
                    Path result = getName(i);
                    i++;
                    return result;
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public int compareTo(Path other) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("Conversion to File not supported.");
    }

    @Override
    public String toString() {
        return (isAbsolute ? DELIMETER : "") + String.join(DELIMETER, path);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.getFileSystem());
        hash = 89 * hash + Objects.hashCode(this.path);
        hash = 89 * hash + (this.isAbsolute ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractPath other = (AbstractPath) obj;
        if (this.isAbsolute != other.isAbsolute) {
            return false;
        }
        if (!Objects.equals(this.getFileSystem(), other.getFileSystem())) {
            return false;
        }
        return Objects.equals(this.path, other.path);
    }

}
