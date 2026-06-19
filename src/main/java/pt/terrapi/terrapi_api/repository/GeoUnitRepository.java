package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.terrapi.terrapi_api.dto.GeoUnitMinimalDto;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

public interface GeoUnitRepository extends JpaRepository<GeoUnit, String> {
    List<GeoUnit> findByType(GeoUnitType type);
    List<GeoUnit> findByParentCode(String parentCode);

    @Query("SELECT gu FROM GeoUnit gu JOIN FETCH gu.parent WHERE gu.code = :code")
    Optional<GeoUnit> findByIdWithParent(@Param("code") String code);

    @Query(value = """
            WITH RECURSIVE ancestor_chain AS (
                SELECT * FROM geo_units WHERE code = :code
                UNION ALL
                SELECT gu.* FROM geo_units gu
                JOIN ancestor_chain ac ON gu.code = ac.parent_code
            )
            SELECT * FROM ancestor_chain WHERE code != :code
            """, nativeQuery = true)
    List<GeoUnit> findAncestorsRecursive(@Param("code") String code);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitMinimalDto(
                gu.code,
                CASE WHEN gu.simplifiedName IS NOT NULL THEN gu.simplifiedName ELSE gu.name END,
                gu.type,
                gu.parent.code)
            FROM GeoUnit gu
            """)
    List<GeoUnitMinimalDto> findAllMinimal();

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitMinimalDto(
                gu.code,
                CASE WHEN gu.simplifiedName IS NOT NULL THEN gu.simplifiedName ELSE gu.name END,
                gu.type,
                gu.parent.code)
            FROM GeoUnit gu
            WHERE gu.type = :type
            """)
    List<GeoUnitMinimalDto> findByTypeMinimal(@Param("type") GeoUnitType type);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitMinimalDto(
                gu.code,
                CASE WHEN gu.simplifiedName IS NOT NULL THEN gu.simplifiedName ELSE gu.name END,
                gu.type,
                gu.parent.code)
            FROM GeoUnit gu
            WHERE gu.parent.code = :parentCode
            """)
    List<GeoUnitMinimalDto> findByParentCodeMinimal(@Param("parentCode") String parentCode);
}
