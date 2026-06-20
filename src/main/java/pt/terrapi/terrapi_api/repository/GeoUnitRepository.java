package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryProjection;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

public interface GeoUnitRepository extends JpaRepository<GeoUnit, String> {

    @Query(value = """
            SELECT gu.code,
                   gu.simplified_name AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent_code AS parentCode
            FROM geo_units gu
            WHERE gu.code = :code
            """, nativeQuery = true)
    Optional<GeoUnitSummaryProjection> findSummaryById(@Param("code") String code);

    @Query(value = """
            SELECT gu.code,
                   gu.simplifiedName AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent.code AS parentCode
            FROM GeoUnit gu
            """,
            countQuery = "SELECT COUNT(gu) FROM GeoUnit gu")
    Page<GeoUnitSummaryProjection> findAllMinimal(Pageable pageable);

    @Query(value = """
            SELECT gu.code,
                   gu.simplifiedName AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent.code AS parentCode
            FROM GeoUnit gu
            WHERE gu.type = :type
            """,
            countQuery = "SELECT COUNT(gu) FROM GeoUnit gu WHERE gu.type = :type")
    Page<GeoUnitSummaryProjection> findByTypeMinimal(@Param("type") GeoUnitType type, Pageable pageable);

    @Query("""
            SELECT gu.code,
                   gu.simplifiedName AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent.code AS parentCode
            FROM GeoUnit gu
            WHERE gu.parent.code = :parentCode
            """)
    List<GeoUnitSummaryProjection> findByParentCodeMinimal(@Param("parentCode") String parentCode);

    @Query("""
            SELECT gu.code,
                   gu.simplifiedName AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent.code AS parentCode
            FROM GeoUnit gu
            WHERE gu.type = :type
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryProjection> findByTypeList(@Param("type") GeoUnitType type);

    @Query("""
            SELECT gu.code,
                   gu.simplifiedName AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent.code AS parentCode
            FROM GeoUnit gu
            WHERE gu.parent.code = :parentCode AND gu.type = :type
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryProjection> findByParentCodeAndTypeMinimal(@Param("parentCode") String parentCode, @Param("type") GeoUnitType type);

    @Query("""
            SELECT gu.code,
                   gu.simplifiedName AS simplifiedName,
                   gu.name,
                   gu.type,
                   gu.parent.code AS parentCode
            FROM GeoUnit gu
            WHERE gu.parent.parent.code = :grandparentCode AND gu.type = :type
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryProjection> findByGrandparentCodeAndTypeMinimal(@Param("grandparentCode") String grandparentCode, @Param("type") GeoUnitType type);
}
