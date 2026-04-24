package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * Records a single change of the default version for a versioned file.
 *
 * @author tonyj
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefaultChangeRecord implements Serializable {

    private final int version;
    private final long timestamp;
    private final String changedBy;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public DefaultChangeRecord(
            @JsonProperty("version") int version,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("changedBy") String changedBy) {
        this.version = version;
        this.timestamp = timestamp;
        this.changedBy = changedBy == null ? "" : changedBy;
    }

    public int getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
