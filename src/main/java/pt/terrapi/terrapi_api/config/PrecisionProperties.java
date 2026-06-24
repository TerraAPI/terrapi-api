package pt.terrapi.terrapi_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties("terrapi.precision")
public class PrecisionProperties {

    /** The LOD ladder (one tolerance per level) applied per type independently. */
    private List<LodLevel> lod;
    private Validation validation = new Validation();

    private boolean simplifyBoundary = false;

    @Getter
    @Setter
    public static class Validation {
        private double maxNullPct = 1.0;
        private double maxInvalidPct = 1.0;
        private double maxEmptyPct = 1.0;
    }
}
