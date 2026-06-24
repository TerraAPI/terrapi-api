package pt.terrapi.terrapi_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties("terrapi.precision")
public class PrecisionProperties {

    /** Per-type LOD ladders (keyed by lowercase GeoUnitType name, e.g. "parish"). */
    private Map<String, List<LodLevel>> ladders = new HashMap<>();
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
