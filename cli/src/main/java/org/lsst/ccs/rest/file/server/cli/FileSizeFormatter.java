package org.lsst.ccs.rest.file.server.cli;

/**
 * A formatter for file size. tries to emulate ls
 * @author tonyj
 */
public class FileSizeFormatter {

    private final boolean humanReadable;
    private final boolean si;

    FileSizeFormatter(boolean humanReadable, boolean si) {
        this.humanReadable = humanReadable;
        this.si = si;
    }
    
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
