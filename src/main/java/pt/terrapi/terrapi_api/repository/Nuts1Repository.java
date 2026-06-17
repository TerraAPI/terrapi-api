package pt.terrapi.terrapi_api.repository;

import java.util.Optional;
import pt.terrapi.terrapi_api.entities.Nuts1;

public interface Nuts1Repository extends BaseGeoRepository<Nuts1> {
    Optional<Nuts1> findByName(String name);
}
