package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author tonyj
 */
public class VersionOptions {
    
    private final int version;
    private final Boolean hidden;
    private final String comment;
    private final Boolean makeDefault;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VersionOptions(@JsonProperty(value = "version") int version, @JsonProperty(value = "hidden") Boolean hidden, @JsonProperty(value = "comment") String comment, @JsonProperty(value = "default") Boolean makeDefault) {
        this.version = version;
        this.hidden = hidden;
        this.comment = comment;
        this.makeDefault = makeDefault;
    }

    public int getVersion() {
        return version;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public String getComment() {
        return comment;
    }

    public Boolean getMakeDefault() {
        return makeDefault;
    }

    public static class Builder {

        private final int version;
        private String comment;
        private boolean hidden;
        private boolean defaultVersion;

        public Builder(int version) {
            this.version = version;
        }
        
        public Builder setComment(String comment) {
            this.comment = comment;
            return this;
        }
        
        public Builder setHidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }
        
        public Builder setDefault() {
            this.defaultVersion = true;
            return this;
        }
        
        public VersionOptions build() {
            return new VersionOptions(version, hidden, comment, defaultVersion);
        }
    }
    
}
