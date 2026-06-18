package pt.terrapi.terrapi_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.enums.AdminUnitType;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrecisionPolicyService {

    private final PrecisionProperties properties;

    public List<LodLevel> getLodLevels(AdminUnitType type) {
        return properties.getLod().get(type.name());
    }
}
