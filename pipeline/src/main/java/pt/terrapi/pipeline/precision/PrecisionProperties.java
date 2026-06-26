package pt.terrapi.pipeline.precision;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties("terrapi.precision")
public class PrecisionProperties {

    /** Single LOD ladder shared by all {@code GeoUnitType}s (EPSG:3763 metre tolerances). */
    private List<LodLevel> ladder = new ArrayList<>();
    private Validation validation = new Validation();

    private boolean simplifyBoundary = true;

    @Getter
    @Setter
    public static class Validation {
        private double maxNullPct = 1.0;
        private double maxInvalidPct = 1.0;
        private double maxEmptyPct = 1.0;
    }
}
