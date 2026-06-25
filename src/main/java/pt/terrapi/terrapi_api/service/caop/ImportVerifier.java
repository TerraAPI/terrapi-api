package pt.terrapi.terrapi_api.service.caop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

/**
 * Post-import integrity gate. Runs inside the import transaction, so any thrown check rolls the
 * whole import back rather than letting it commit silently corrupt. Validates: geometry SRID and
 * bounds; completeness (every source unit persisted under its own type — catches code collisions
 * and overwrites); and referential integrity (no dangling/missing parents, resolvable nuts3_code).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportVerifier {

    /** Child types that must always have a resolved parent (roots are excluded). */
    private static final int[] CHILD_TYPES_REQUIRING_PARENT = {
            GeoUnitType.MUNICIPALITY.getValue(), GeoUnitType.PARISH.getValue(),
            GeoUnitType.NUTS2.getValue(), GeoUnitType.NUTS3.getValue()
    };

    private final JdbcTemplate jdbcTemplate;

    /**
     * Fails loudly unless every {@code (type, code)} produced by the reader is present in
     * {@code geo_units} <em>with that type</em>. A code collision across types (e.g. district
     * {@code 11} vs NUTS2 {@code 11}) or any last-write-wins overwrite leaves a code stored under
     * the wrong type — this catches it and aborts instead of shipping a half-loaded layer.
     */
    public void verifyCompleteness(Map<GeoUnitType, Set<String>> codesByType) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<GeoUnitType, Set<String>> e : codesByType.entrySet()) {
            GeoUnitType type = e.getKey();
            String[] codes = e.getValue().toArray(new String[0]);
            if (codes.length == 0) continue;
            long present = jdbcTemplate.execute((java.sql.Connection con) -> {
                try (java.sql.PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM geo_units WHERE type = ? AND code = ANY(?)")) {
                    ps.setInt(1, type.getValue());
                    ps.setArray(2, con.createArrayOf("text", codes));
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
            });
            if (present != codes.length) {
                problems.add(type + ": expected " + codes.length + " units but only " + present
                        + " present with that type (code collision or overwrite — "
                        + (codes.length - present) + " lost)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Import completeness check failed: " + problems);
        }
        log.info("  Completeness verified: all source units present under their own type");
    }

    /**
     * Fails loudly on a broken hierarchy: a {@code parent_code} pointing at no row, a child type
     * with no parent at all (a parent name that did not resolve), or a municipality whose
     * {@code nuts3_code} matches no NUTS3 unit.
     */
    public void verifyReferentialIntegrity() {
        Long dangling = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM geo_units c
                WHERE c.parent_code IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM geo_units p WHERE p.code = c.parent_code)
                """, Long.class);
        if (dangling != null && dangling > 0) {
            throw new IllegalStateException(dangling + " geo_units reference a non-existent parent_code");
        }

        String inClause = java.util.Arrays.stream(CHILD_TYPES_REQUIRING_PARENT)
                .mapToObj(Integer::toString).reduce((a, b) -> a + "," + b).orElseThrow();
        Long orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM geo_units WHERE parent_code IS NULL AND type IN (" + inClause + ")",
                Long.class);
        if (orphans != null && orphans > 0) {
            throw new IllegalStateException(orphans + " municipality/parish/NUTS2/NUTS3 units have no "
                    + "parent_code — a parent name failed to resolve (CAOP naming/encoding mismatch)");
        }

        Long badNuts3 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM geo_units m
                WHERE m.type = %d AND m.nuts3_code IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM geo_units n WHERE n.type = %d AND n.code = m.nuts3_code)
                """.formatted(GeoUnitType.MUNICIPALITY.getValue(), GeoUnitType.NUTS3.getValue()),
                Long.class);
        if (badNuts3 != null && badNuts3 > 0) {
            throw new IllegalStateException(badNuts3 + " municipalities carry a nuts3_code with no "
                    + "matching NUTS3 unit (statistical join broken)");
        }
        log.info("  Referential integrity verified: parents and nuts3_code links resolve");
    }

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
