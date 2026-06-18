package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.terrapi.terrapi_api.entities.StatUnit;
import pt.terrapi.terrapi_api.enums.StatUnitLevel;

public interface StatUnitRepository extends JpaRepository<StatUnit, Long> {
    Optional<StatUnit> findByCode(String code);
    List<StatUnit> findByLevel(StatUnitLevel level);
    List<StatUnit> findByParentId(Long parentId);

    @Query("SELECT s.code, s.id FROM StatUnit s")
    List<Object[]> findAllCodesAndIds();
}
