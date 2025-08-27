package org.lsst.ccs.rest.file.server.cli;

import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formats file modification times in a style similar to the Unix {@code ls}
 * command.
 *
 * @author tonyj
 */
public class FileDateFormatter {

    private final static Duration SWITCH_FORMAT_AGE = Duration.ofDays(200);
    private final static DateTimeFormatter RECENT_FORMAT = DateTimeFormatter.ofPattern("MMM dd HH:mm");
    private final static DateTimeFormatter AGED_FORMAT = DateTimeFormatter.ofPattern("MMM dd  YYYY");
    private final static DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("YYYY-MM-DD hh:mm:ss");
    private final boolean fullTime;
    private final Instant referenceTime;

    
    /**
     * Creates a formatter.
     *
     * @param fullTime if {@code true}, always use the full time format rather
     *        than switching based on age
     */
    FileDateFormatter(boolean fullTime) {
        this.fullTime = fullTime;
        referenceTime = Instant.now();
    }

    /**
     * Formats the supplied file time.
     *
     * @param time the file time to format
     * @return the formatted time string
     */
    String format(FileTime time) {
        Instant timeStamp = time.toInstant();
        Duration age = Duration.between(referenceTime, timeStamp);
        DateTimeFormatter formatter;
        if (fullTime) {
            formatter = FULL_FORMAT;
        } else if (age.compareTo(SWITCH_FORMAT_AGE)<0) {
            formatter = RECENT_FORMAT;  
        } else {
            formatter = AGED_FORMAT;
        }
        return formatter.withZone(ZoneId.systemDefault()).format(timeStamp);
    }


}
