package pt.terrapi.terrapi_api.service.precision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrecisionPolicyService {

    private final PrecisionProperties properties;

    public List<LodLevel> getLodLevels(GeoUnitType type) {
        return properties.getLod().get(type.name());
    }

    public boolean isTopologyPreserving(GeoUnitType type) {
        Map<String, Boolean> overrides = properties.getTopologyPreservingByType();
        if (overrides != null && overrides.containsKey(type.name())) {
            return overrides.get(type.name());
        }
        return properties.isTopologyPreserving();
    }

    public boolean isSimplifyBoundary() {
        return properties.isSimplifyBoundary();
    }

    public double getCoverageSnapTolerance() {
        return properties.getCoverageSnapTolerance();
    }
}
