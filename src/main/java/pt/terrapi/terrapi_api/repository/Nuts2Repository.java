package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import pt.terrapi.terrapi_api.entities.Nuts2;

public interface Nuts2Repository extends BaseGeoRepository<Nuts2> {
    Optional<Nuts2> findByName(String name);
    List<Nuts2> findByParentId(Long parentId);
}
