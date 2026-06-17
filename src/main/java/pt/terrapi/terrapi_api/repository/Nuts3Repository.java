package pt.terrapi.terrapi_api.repository;

import java.util.List;
import pt.terrapi.terrapi_api.entities.Nuts3;

public interface Nuts3Repository extends BaseGeoRepository<Nuts3> {
    List<Nuts3> findByParentId(Long parentId);
}
