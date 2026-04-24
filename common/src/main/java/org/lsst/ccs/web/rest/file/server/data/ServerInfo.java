package org.lsst.ccs.web.rest.file.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * Server version and capability information returned by the serverInfo
 * endpoint.
 */
public class ServerInfo implements Serializable {

    private final String version;
    private final List<String> capabilities;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ServerInfo(
            @JsonProperty("version") String version,
            @JsonProperty("capabilities") List<String> capabilities) {
        this.version = version;
        this.capabilities = capabilities;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }
}
