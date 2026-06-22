package pt.terrapi.terrapi_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.GeoUnitPrecision;

public interface GeoUnitPrecisionRepository extends JpaRepository<GeoUnitPrecision, Long> {
}
