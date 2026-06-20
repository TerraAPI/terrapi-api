package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryProjection;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

public interface GeoUnitRepository extends JpaRepository<GeoUnit, String> {

    /**
     * Shared {@link GeoUnitSummaryDto} constructor projection. References the {@code gu} and
     * {@code p} aliases, which the concrete query must provide via {@link #FROM_GEO_UNIT} plus a
     * parent join (LEFT JOIN to keep parent-less units, JOIN only when the WHERE clause filters
     * on the parent and therefore already guarantees one).
     */
    String SUMMARY_PROJECTION = """
            SELECT new pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto(
                gu.code,
                COALESCE(gu.simplifiedName, gu.name),
                gu.type,
                p.code)
            """;

    String FROM_GEO_UNIT = """
            FROM GeoUnit gu
            """;

    @Query(value = """
            SELECT gu.code
            FROM geo_units gu
            WHERE ST_Contains(gu.geometry, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
            AND gu.type = :type
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findContainingCode(@Param("lon") double lon, @Param("lat") double lat, @Param("type") int type);

    @Query(value = """
            SELECT COALESCE(ST_Contains(gu.geometry, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)), false)
            FROM geo_units gu
            WHERE gu.code = :code
            """, nativeQuery = true)
    Optional<Boolean> isPointInside(@Param("code") String code, @Param("lon") double lon, @Param("lat") double lat);

    @Query(value = """
            SELECT ST_AsGeoJSON(ST_SimplifyPreserveTopology(gu.geometry, :tolerance))
            FROM geo_units gu
            WHERE gu.code = :code
            """, nativeQuery = true)
    Optional<String> findGeoJsonByCode(@Param("code") String code, @Param("tolerance") double tolerance);

    @Query(value = """
            SELECT gu.code AS code,
                   COALESCE(gu.simplified_name, gu.name) AS name,
                   gu.type AS type,
                   gu.parent_code AS parentCode
            FROM geo_units gu
            WHERE gu.type = :type
            AND ST_Intersects(gu.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
            ORDER BY name
            """, nativeQuery = true)
    List<GeoUnitSummaryProjection> findSummaryInBbox(@Param("type") int type,
            @Param("minLon") double minLon, @Param("minLat") double minLat,
            @Param("maxLon") double maxLon, @Param("maxLat") double maxLat);

    @Query(value = """
            SELECT gu.code AS code,
                   COALESCE(gu.simplified_name, gu.name) AS name,
                   gu.type AS type,
                   gu.parent_code AS parentCode
            FROM geo_units gu
            WHERE gu.type = :type
            AND ST_DWithin(gu.geometry::geography,
                           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                           :radiusMeters)
            ORDER BY ST_Distance(gu.geometry::geography,
                                 ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
            """, nativeQuery = true)
    List<GeoUnitSummaryProjection> findSummaryWithinRadius(@Param("type") int type,
            @Param("lon") double lon, @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters);

    @Query(value = SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " LEFT JOIN gu.parent p",
            countQuery = "SELECT COUNT(gu) FROM GeoUnit gu")
    Page<GeoUnitSummaryDto> findSummaryPage(Pageable pageable);

    @Query(value = SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " LEFT JOIN gu.parent p WHERE gu.type = :type",
            countQuery = "SELECT COUNT(gu) FROM GeoUnit gu WHERE gu.type = :type")
    Page<GeoUnitSummaryDto> findSummaryPageByType(@Param("type") GeoUnitType type, Pageable pageable);

    @Query(SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " LEFT JOIN gu.parent p WHERE gu.type = :type ORDER BY gu.name")
    List<GeoUnitSummaryDto> findSummaryListByType(@Param("type") GeoUnitType type);

    @Query(SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " JOIN gu.parent p WHERE p.code = :parentCode ORDER BY gu.name")
    List<GeoUnitSummaryDto> findSummaryListByParentCode(@Param("parentCode") String parentCode);

    @Query(SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " JOIN gu.parent p WHERE p.code = :parentCode AND gu.type = :type ORDER BY gu.name")
    List<GeoUnitSummaryDto> findSummaryListByParentCodeAndType(@Param("parentCode") String parentCode, @Param("type") GeoUnitType type);

    @Query(SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " JOIN gu.parent p WHERE p.parent.code = :grandparentCode AND gu.type = :type ORDER BY gu.name")
    List<GeoUnitSummaryDto> findSummaryListByGrandparentCodeAndType(@Param("grandparentCode") String grandparentCode, @Param("type") GeoUnitType type);

    @Query(SUMMARY_PROJECTION + FROM_GEO_UNIT
            + " LEFT JOIN gu.parent p WHERE gu.nuts3Code = :nuts3Code ORDER BY gu.name")
    List<GeoUnitSummaryDto> findSummaryListByNuts3Code(@Param("nuts3Code") String nuts3Code);
}
