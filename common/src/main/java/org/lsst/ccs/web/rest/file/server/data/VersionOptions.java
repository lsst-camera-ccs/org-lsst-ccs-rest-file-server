package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Options used when creating or updating a versioned file via the REST file
 * server.
 *
 * @author tonyj
 */
public class VersionOptions {
    
    private final int version;
    private final Boolean hidden;
    private final String comment;
    private final Boolean makeDefault;

    /**
     * Creates a new {@code VersionOptions} instance from JSON properties.
     *
     * @param version version number the options apply to
     * @param hidden whether the version should be hidden
     * @param comment comment associated with the version
     * @param makeDefault whether the version should be made the default
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VersionOptions(@JsonProperty(value = "version") int version, @JsonProperty(value = "hidden") Boolean hidden, @JsonProperty(value = "comment") String comment, @JsonProperty(value = "default") Boolean makeDefault) {
        this.version = version;
        this.hidden = hidden;
        this.comment = comment;
        this.makeDefault = makeDefault;
    }

    /**
     * Gets the version number this options object applies to.
     *
     * @return version number
     */
    public int getVersion() {
        return version;
    }

    /**
     * Indicates whether the version should be hidden.
     *
     * @return {@code Boolean.TRUE} if the version should be hidden, otherwise {@code Boolean.FALSE} or {@code null}
     */
    public Boolean getHidden() {
        return hidden;
    }

    /**
     * Gets the comment associated with the version.
     *
     * @return version comment
     */
    public String getComment() {
        return comment;
    }

    /**
     * Indicates whether the version should become the default.
     *
     * @return {@code Boolean.TRUE} if the version should be the default, otherwise {@code Boolean.FALSE} or {@code null}
     */
    public Boolean getMakeDefault() {
        return makeDefault;
    }

    /**
     * Builder for {@link VersionOptions} instances.
     */
    public static class Builder {

        private final int version;
        private String comment;
        private boolean hidden;
        private boolean defaultVersion;

        /**
         * Creates a new builder for the specified version.
         *
         * @param version version number
         */
        public Builder(int version) {
            this.version = version;
        }

        /**
         * Sets the comment for the version.
         *
         * @param comment comment text
         * @return this builder for chaining
         */
        public Builder setComment(String comment) {
            this.comment = comment;
            return this;
        }

        /**
         * Marks the version as hidden.
         *
         * @param hidden {@code true} to hide the version
         * @return this builder for chaining
         */
        public Builder setHidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        /**
         * Marks the version as the default.
         *
         * @return this builder for chaining
         */
        public Builder setDefault() {
            this.defaultVersion = true;
            return this;
        }

        /**
         * Builds a {@link VersionOptions} instance.
         *
         * @return configured {@code VersionOptions}
         */
        public VersionOptions build() {
            return new VersionOptions(version, hidden, comment, defaultVersion);
        }
    }
    
}
