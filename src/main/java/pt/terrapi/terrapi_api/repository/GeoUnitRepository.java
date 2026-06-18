package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

public interface GeoUnitRepository extends JpaRepository<GeoUnit, String> {
    Optional<GeoUnit> findByCode(String code);
    List<GeoUnit> findByType(GeoUnitType type);
    List<GeoUnit> findByParentCode(String parentCode);
}
