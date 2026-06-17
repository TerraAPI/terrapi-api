package pt.terrapi.terrapi_api.repository;

import java.util.List;
import pt.terrapi.terrapi_api.entities.Parish;

public interface ParishRepository extends BaseGeoRepository<Parish> {
    List<Parish> findByMunicipalityId(Long municipalityId);
}
