package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

public interface GeoUnitRepository extends JpaRepository<GeoUnit, String> {

    @Query(value = """
            SELECT gu.code
            FROM geo_units gu
            WHERE ST_Contains(gu.geometry, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
            AND gu.type = :type
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findContainingCode(@Param("lon") double lon, @Param("lat") double lat, @Param("type") int type);

    @Query(value = """
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            """,
            countQuery = "SELECT COUNT(gu) FROM GeoUnit gu")
    Page<GeoUnitSummaryDto> findAllMinimal(Pageable pageable);

    @Query(value = """
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            WHERE gu.type = :type
            """,
            countQuery = "SELECT COUNT(gu) FROM GeoUnit gu WHERE gu.type = :type")
    Page<GeoUnitSummaryDto> findByTypeMinimal(@Param("type") GeoUnitType type, Pageable pageable);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            WHERE p.code = :parentCode
            """)
    List<GeoUnitSummaryDto> findByParentCodeMinimal(@Param("parentCode") String parentCode);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            WHERE gu.type = :type
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryDto> findByTypeList(@Param("type") GeoUnitType type);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            WHERE p.code = :parentCode AND gu.type = :type
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryDto> findByParentCodeAndTypeMinimal(@Param("parentCode") String parentCode, @Param("type") GeoUnitType type);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            WHERE p.parent.code = :grandparentCode AND gu.type = :type
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryDto> findByGrandparentCodeAndTypeMinimal(@Param("grandparentCode") String grandparentCode, @Param("type") GeoUnitType type);

    @Query("""
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            FROM GeoUnit gu
            LEFT JOIN gu.parent p
            WHERE gu.nuts3Code = :nuts3Code
            ORDER BY gu.name
            """)
    List<GeoUnitSummaryDto> findByNuts3CodeMinimal(@Param("nuts3Code") String nuts3Code);
}
