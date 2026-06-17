package pt.terrapi.terrapi_api.repository;

import java.util.Optional;
import pt.terrapi.terrapi_api.entities.District;

public interface DistrictRepository extends BaseGeoRepository<District> {
    Optional<District> findByName(String name);
}
