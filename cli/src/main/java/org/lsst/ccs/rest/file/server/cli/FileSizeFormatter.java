package org.lsst.ccs.rest.file.server.cli;

/**
 * Formats file sizes in a style similar to the Unix {@code ls} command.
 *
 * @author tonyj
 */
public class FileSizeFormatter {

    private final boolean humanReadable;
    private final boolean si;

    /**
     * Creates a formatter.
     *
     * @param humanReadable whether to use human readable units
     * @param si if {@code true}, use powers of 1000; otherwise use 1024
     */
    FileSizeFormatter(boolean humanReadable, boolean si) {
        this.humanReadable = humanReadable;
        this.si = si;
    }

    /**
     * Formats the supplied size.
     *
     * @param size the size in bytes
     * @return a string representation of the size
     */
    String format(long size) {
        if (humanReadable) {
            return humanReadableByteCount(size, si);
        } else {
            return String.format("%d", size);
        }
    }

    private static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + "";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "");
        return String.format("%.1f%s", bytes / Math.pow(unit, exp), pre);
    }
}
