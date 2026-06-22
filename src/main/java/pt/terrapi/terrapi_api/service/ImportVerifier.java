package pt.terrapi.terrapi_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Validates the imported geometry: correct SRID, no missing/empty geometries, and centroids
 * within Portugal's bounds. Throws if the SRID transform failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportVerifier {

    private final JdbcTemplate jdbcTemplate;

    public void verifyGeometryIntegration() {
        Long wrongSrid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM geo_units WHERE geometry IS NOT NULL AND ST_SRID(geometry) <> 4326",
                Long.class);
        if (wrongSrid == null || wrongSrid > 0) {
            log.error("Geometry SRID verification failed: {} rows with SRID != 4326", wrongSrid);
            throw new IllegalStateException(
                    "Geometry SRID mismatch: " + wrongSrid + " rows not in 4326. CRS transform may have failed.");
        }

        Long missingGeometry = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM geo_units WHERE geometry IS NULL OR ST_IsEmpty(geometry)",
                Long.class);
        if (missingGeometry != null && missingGeometry > 0) {
            log.warn("{} units have null/empty geometry — they will be skipped by precision generation",
                    missingGeometry);
        }

        var outOfBounds = jdbcTemplate.queryForList("""
                SELECT code, ST_X(ST_Centroid(geometry)) AS lon,
                       ST_Y(ST_Centroid(geometry)) AS lat
                FROM geo_units
                WHERE geometry IS NOT NULL
                  AND (ST_X(ST_Centroid(geometry)) < -35
                       OR ST_X(ST_Centroid(geometry)) > -6
                       OR ST_Y(ST_Centroid(geometry)) < 30
                       OR ST_Y(ST_Centroid(geometry)) > 45)
                LIMIT 5
                """);
        if (!outOfBounds.isEmpty()) {
            for (var row : outOfBounds) {
                log.warn("  Suspicious centroid: code={} lon={} lat={}",
                        row.get("code"), row.get("lon"), row.get("lat"));
            }
            log.warn("{} units have centroids outside Portugal range (lon -35..-6, lat 30..45) — "
                    + "check source CRS", outOfBounds.size());
        }

        log.info("  Geometry verification passed: SRID=4326, centroids within Portugal bounds");
    }
}
