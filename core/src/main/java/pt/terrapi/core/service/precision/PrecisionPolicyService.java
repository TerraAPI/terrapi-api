package pt.terrapi.core.service.precision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrecisionPolicyService {

    private final PrecisionProperties properties;

    /** The single LOD ladder, shared by all {@code GeoUnitType}s. */
    public List<LodLevel> getLodLadder() {
        return properties.getLadder();
    }

    public boolean isSimplifyBoundary() {
        return properties.isSimplifyBoundary();
    }
}
