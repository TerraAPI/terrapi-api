package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import pt.terrapi.terrapi_api.entities.Municipality;

public interface MunicipalityRepository extends BaseGeoRepository<Municipality> {
    Optional<Municipality> findByName(String name);
    List<Municipality> findByDistrictId(Long districtId);
}
