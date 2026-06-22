package pt.terrapi.terrapi_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional writer for {@code geo_unit_precisions}. Replaces (delete + insert) the precision
 * rows for the requested types and LODs, then validates the result. A failed validation throws,
 * rolling back the whole block so the previous rows are preserved.
 */
@Slf4j
@Service
public class PrecisionWriter {

    private static final String INSERT_LODS_SQL = """
            WITH lods(lod, tolerance) AS (
                VALUES %s
            ),
            base AS (
                SELECT u.code,
                       ST_Transform(
                           CASE WHEN ST_SRID(u.geometry) = 0
                                THEN ST_SetSRID(u.geometry, 4326)
                                ELSE u.geometry END,
                           3763) AS geom_3763
                FROM geo_units u
                WHERE u.type = ?
                  AND u.geometry IS NOT NULL
                  AND NOT ST_IsEmpty(u.geometry)
            ),
            simplified AS (
                SELECT b.code, l.lod, l.tolerance,
                       %s AS simplified_3763
                FROM base b
                CROSS JOIN lods l
            )
            INSERT INTO geo_unit_precisions
                (geo_unit_code, type, lod, geometry, tolerance_m, vertex_count,
                 generation_id, created_at)
            SELECT code, ?, lod, ST_Transform(simplified_3763, 3857), tolerance,
                   ST_NPoints(simplified_3763), ?, NOW()
            FROM simplified
            """;

    private static final String PERFEATURE_EXPR =
            "ST_SimplifyPreserveTopology(b.geom_3763, l.tolerance)";

    private static final String COVERAGE_EXPR =
            "ST_SetSRID(ST_CoverageSimplify(b.geom_3763, l.tolerance, %s) OVER (PARTITION BY l.lod), 3763)";

    private static final String UNIT_COUNT_SQL =
            "SELECT COUNT(*) FROM geo_units WHERE type = ? "
                    + "AND geometry IS NOT NULL AND NOT ST_IsEmpty(geometry)";

    private static final String DELETE_TYPE_SQL =
            "DELETE FROM geo_unit_precisions WHERE type = ?";

    private static final String DELETE_TYPE_LOD_SQL =
            "DELETE FROM geo_unit_precisions WHERE type = ? AND lod = ?";

    private static final String COVERAGE_VALIDITY_SQL = """
            SELECT COALESCE(COUNT(*) FILTER (WHERE inv IS NOT NULL), 0)
            FROM (
                SELECT ST_CoverageInvalidEdges(
                           ST_Transform(
                               CASE WHEN ST_SRID(geometry) = 0
                                    THEN ST_SetSRID(geometry, 4326)
                                    ELSE geometry END,
                               3763), ?) OVER () AS inv
                FROM geo_units
                WHERE type = ?
                  AND geometry IS NOT NULL
                  AND NOT ST_IsEmpty(geometry)
            ) s
            """;

    private static final String VALIDATE_SQL = """
            SELECT COALESCE(COUNT(*), 0) AS total_rows,
                   COALESCE(COUNT(*) FILTER (WHERE geometry IS NULL), 0) AS null_count,
                   COALESCE(COUNT(*) FILTER (WHERE NOT ST_IsValid(geometry)), 0) AS invalid_count,
                   COALESCE(COUNT(*) FILTER (WHERE ST_IsEmpty(geometry)), 0) AS empty_count
            FROM geo_unit_precisions
            WHERE generation_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PrecisionPolicyService policyService;
    private final PrecisionProperties properties;

    public PrecisionWriter(JdbcTemplate jdbcTemplate,
                           PrecisionPolicyService policyService,
                           PrecisionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.policyService = policyService;
        this.properties = properties;
    }

    /**
     * Replaces and validates precision rows for the given types and (optional) LOD in one
     * transaction. Throws {@link GenerationFailedException} on an unhealthy result, rolling back.
     */
    @Transactional
    public WriteResult write(UUID generationId, List<GeoUnitType> types, Integer lod) {
        int totalLods = 0;
        int totalUnits = 0;
        boolean degraded = false;

        for (GeoUnitType type : types) {
            List<LodLevel> levels = levelsFor(type, lod);
            if (levels.isEmpty()) continue;

            deleteScope(type, lod);

            totalUnits += unitCount(type);
            totalLods += levels.size();

            boolean wantCoverage = policyService.isTopologyPreserving(type);
            boolean useCoverage = wantCoverage && isValidCoverage(type);
            if (wantCoverage && !useCoverage) {
                degraded = true;
                log.warn("    {}: not a valid coverage — falling back to per-feature simplification", type);
            }
            int rows = insertLods(type, levels, generationId, useCoverage);
            log.info("    {}: {} rows ({})", type, rows, useCoverage ? "coverage" : "per-feature");
        }

        ValidationResult validation = validate(generationId);
        if (!isHealthy(validation, totalUnits, totalLods)) {
            throw new GenerationFailedException(
                    "Generation " + generationId + " unhealthy — rows=" + validation.totalRows
                            + " null=" + pct(validation.nullCount, validation.totalRows) + "%"
                            + " invalid=" + pct(validation.invalidCount, validation.totalRows) + "%"
                            + " empty=" + pct(validation.emptyCount, validation.totalRows) + "%");
        }

        return new WriteResult(validation.totalRows, validation.nullCount, validation.invalidCount,
                totalUnits, totalLods, degraded);
    }

    private List<LodLevel> levelsFor(GeoUnitType type, Integer lod) {
        List<LodLevel> levels = policyService.getLodLevels(type);
        if (levels == null || levels.isEmpty()) return List.of();
        if (lod == null) return levels;
        return levels.stream().filter(l -> l.lod() == lod).toList();
    }

    private void deleteScope(GeoUnitType type, Integer lod) {
        if (lod == null) {
            jdbcTemplate.update(DELETE_TYPE_SQL, type.getValue());
        } else {
            jdbcTemplate.update(DELETE_TYPE_LOD_SQL, type.getValue(), lod);
        }
    }

    private int unitCount(GeoUnitType type) {
        Long count = jdbcTemplate.queryForObject(UNIT_COUNT_SQL, Long.class, type.getValue());
        return count != null ? count.intValue() : 0;
    }

    private int insertLods(GeoUnitType type, List<LodLevel> levels, UUID generationId, boolean coverage) {
        String valuesClause = levels.stream()
                .map(l -> String.format(Locale.US, "(%d, %.1f)", l.lod(), l.tolerance()))
                .collect(Collectors.joining(", "));
        String expr = coverage
                ? String.format(COVERAGE_EXPR, policyService.isSimplifyBoundary() ? "true" : "false")
                : PERFEATURE_EXPR;
        String sql = String.format(INSERT_LODS_SQL, valuesClause, expr);
        return jdbcTemplate.update(sql, type.getValue(), type.getValue(), generationId);
    }

    private boolean isValidCoverage(GeoUnitType type) {
        Integer invalidEdges = jdbcTemplate.queryForObject(COVERAGE_VALIDITY_SQL, Integer.class,
                policyService.getCoverageSnapTolerance(), type.getValue());
        return invalidEdges == null || invalidEdges == 0;
    }

    private ValidationResult validate(UUID generationId) {
        return jdbcTemplate.queryForObject(VALIDATE_SQL,
                (rs, rowNum) -> new ValidationResult(
                        rs.getInt("total_rows"),
                        rs.getInt("null_count"),
                        rs.getInt("invalid_count"),
                        rs.getInt("empty_count")),
                generationId);
    }

    private boolean isHealthy(ValidationResult v, int totalUnits, int totalLods) {
        if (totalUnits == 0 || totalLods == 0) {
            return true;
        }
        PrecisionProperties.Validation t = properties.getValidation();
        return v.totalRows > 0
                && pct(v.nullCount, v.totalRows) <= t.getMaxNullPct()
                && pct(v.invalidCount, v.totalRows) <= t.getMaxInvalidPct()
                && pct(v.emptyCount, v.totalRows) <= t.getMaxEmptyPct();
    }

    private static double pct(int part, int total) {
        return total > 0 ? 100.0 * part / total : 0;
    }

    public record WriteResult(int rowCount, int nullCount, int invalidCount,
                              int totalUnits, int totalLods, boolean degraded) {
        public static WriteResult empty() {
            return new WriteResult(0, 0, 0, 0, 0, false);
        }
    }

    record ValidationResult(int totalRows, int nullCount, int invalidCount, int emptyCount) {}

    public static class GenerationFailedException extends RuntimeException {
        public GenerationFailedException(String message) {
            super(message);
        }
    }
}
