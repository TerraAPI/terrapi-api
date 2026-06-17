package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.AdminUnit;
import pt.terrapi.terrapi_api.enums.AdminUnitType;

public interface AdminUnitRepository extends JpaRepository<AdminUnit, Long> {
    Optional<AdminUnit> findByCode(String code);
    List<AdminUnit> findByType(AdminUnitType type);
    List<AdminUnit> findByParentId(Long parentId);
}
