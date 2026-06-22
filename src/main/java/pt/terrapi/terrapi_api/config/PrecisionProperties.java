package pt.terrapi.terrapi_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties("terrapi.precision")
public class PrecisionProperties {

    private Map<String, List<LodLevel>> lod;
    private Validation validation = new Validation();

    private boolean topologyPreserving = true;
    private boolean simplifyBoundary = false;
    private double coverageSnapTolerance = 0.0;
    private Map<String, Boolean> topologyPreservingByType;

    @Getter
    @Setter
    public static class Validation {
        private double maxNullPct = 1.0;
        private double maxInvalidPct = 1.0;
        private double maxEmptyPct = 1.0;
    }
}
