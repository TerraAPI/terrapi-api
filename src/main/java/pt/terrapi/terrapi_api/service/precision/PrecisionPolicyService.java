package pt.terrapi.terrapi_api.service.precision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrecisionPolicyService {

    private final PrecisionProperties properties;

    /** The LOD ladder (one tolerance per level) driving the nested hierarchy. */
    public List<LodLevel> getLodLadder() {
        return properties.getLod();
    }

    public boolean isSimplifyBoundary() {
        return properties.isSimplifyBoundary();
    }
}
