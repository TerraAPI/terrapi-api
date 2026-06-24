package pt.terrapi.terrapi_api.service.precision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrecisionPolicyService {

    private final PrecisionProperties properties;

    /** The per-type LOD ladder for the given {@link GeoUnitType}. */
    public List<LodLevel> getLodLadder(GeoUnitType type) {
        List<LodLevel> ladder = properties.getLadders().get(type.name().toLowerCase());
        return ladder != null ? ladder : List.of();
    }

    public boolean isSimplifyBoundary() {
        return properties.isSimplifyBoundary();
    }
}
