package pt.terrapi.pipeline.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pt.terrapi.pipeline.config.LodLevel;
import pt.terrapi.pipeline.config.PrecisionProperties;

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
