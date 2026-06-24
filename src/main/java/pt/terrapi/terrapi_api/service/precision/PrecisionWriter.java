package pt.terrapi.terrapi_api.service.precision;

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

/**
 * Transactional writer for {@code geo_unit_precisions}. Each {@link GeoUnitType} is
 * coverage-simplified independently at each LOD from the source geometries in
 * {@code geo_units}. All features of the same type form a valid coverage within their
 * layer; edges are not shared across types. The CAOP data is an authoritative, valid
 * coverage; if it ever isn't, generation fails loudly and rolls back rather than degrading.
 */
@Slf4j
@Service
public class PrecisionWriter {

    private static final String INSERT_TYPE_SQL = """
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
                       ST_SetSRID(ST_CoverageSimplify(b.geom_3763, l.tolerance, %s)
                           OVER (PARTITION BY l.lod), 3763) AS simplified_3763
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

    /**
     * Independently line-simplify each classified border arc ({@code ST_SimplifyPreserveTopology})
     * per LOD. Arc endpoints (shared network nodes) are preserved, so the network stays connected.
     * Edge-level semantics ({@code level}, {@code lineType}) are carried through from the source.
     */
    private static final String INSERT_BORDER_SQL = """
            INSERT INTO border_segment_precisions
                (border_segment_id, level, line_type, length_km, lod, geometry,
                 tolerance_m, vertex_count, generation_id, created_at)
            SELECT id, level, line_type, length_km, ?, ST_Transform(simplified_3763, 3857),
                   ?, ST_NPoints(simplified_3763), ?, NOW()
            FROM (
                SELECT b.id, b.level, b.line_type, b.length_km,
                       ST_SimplifyPreserveTopology(
                           ST_Transform(
                               CASE WHEN ST_SRID(b.geometry) = 0
                                    THEN ST_SetSRID(b.geometry, 4326)
                                    ELSE b.geometry END,
                               3763), ?) AS simplified_3763
                FROM border_segments b
                WHERE b.geometry IS NOT NULL AND NOT ST_IsEmpty(b.geometry)
            ) s
            WHERE simplified_3763 IS NOT NULL AND NOT ST_IsEmpty(simplified_3763)
            """;

    private static final String ALL_UNITS_COUNT_SQL =
            "SELECT COUNT(*) FROM geo_units WHERE geometry IS NOT NULL AND NOT ST_IsEmpty(geometry)";

    private static final String DELETE_ALL_SQL = "DELETE FROM geo_unit_precisions";

    private static final String DELETE_LOD_SQL = "DELETE FROM geo_unit_precisions WHERE lod = ?";

    private static final String DELETE_BORDER_ALL_SQL = "DELETE FROM border_segment_precisions";

    private static final String DELETE_BORDER_LOD_SQL =
            "DELETE FROM border_segment_precisions WHERE lod = ?";

    private static final String VALIDATE_SQL = """
            SELECT COALESCE(COUNT(*), 0) AS total_rows,
                   COALESCE(COUNT(*) FILTER (WHERE geometry IS NULL), 0) AS null_count,
                   COALESCE(COUNT(*) FILTER (WHERE NOT ST_IsValid(geometry)), 0) AS invalid_count,
                   COALESCE(COUNT(*) FILTER (WHERE ST_IsEmpty(geometry)), 0) AS empty_count
            FROM (
                SELECT geometry FROM geo_unit_precisions WHERE generation_id = ?
                UNION ALL
                SELECT geometry FROM border_segment_precisions WHERE generation_id = ?
            ) g
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
     * Rebuilds precisions for all types at all LODs (or a single LOD when given) in one
     * transaction. Throws {@link GenerationFailedException} on an unhealthy result, rolling back.
     */
    @Transactional
    public WriteResult write(UUID generationId, Integer lod) {
        List<LodLevel> ladder = ladderFor(lod);
        if (ladder.isEmpty()) {
            return WriteResult.empty();
        }

        deleteScope(lod);
        for (LodLevel level : ladder) {
            buildLayers(level, generationId);
        }
        log.info("  Generated per-layer precisions for {} LOD level(s)", ladder.size());

        ValidationResult validation = validate(generationId);
        int totalUnits = allUnitsCount();
        int totalLods = ladder.size();
        if (!isHealthy(validation, totalUnits, totalLods)) {
            throw new GenerationFailedException(
                    "Generation " + generationId + " unhealthy — rows=" + validation.totalRows
                            + " null=" + pct(validation.nullCount, validation.totalRows) + "%"
                            + " invalid=" + pct(validation.invalidCount, validation.totalRows) + "%"
                            + " empty=" + pct(validation.emptyCount, validation.totalRows) + "%");
        }

        return new WriteResult(validation.totalRows, validation.nullCount, validation.invalidCount,
                totalUnits, totalLods);
    }

    private void buildLayers(LodLevel level, UUID generationId) {
        for (GeoUnitType type : GeoUnitType.values()) {
            buildLayer(level, generationId, type);
        }
        insertBorderPrecisions(level, generationId);
    }

    private void insertBorderPrecisions(LodLevel level, UUID generationId) {
        jdbcTemplate.update(INSERT_BORDER_SQL,
                level.lod(), level.tolerance(), generationId, level.tolerance());
    }

    private void buildLayer(LodLevel level, UUID generationId, GeoUnitType type) {
        String valuesClause = String.format(Locale.US, "(%d, %.1f)", level.lod(), level.tolerance());
        String sql = String.format(INSERT_TYPE_SQL, valuesClause,
                policyService.isSimplifyBoundary() ? "true" : "false");
        jdbcTemplate.update(sql, type.getValue(), type.getValue(), generationId);
    }

    private List<LodLevel> ladderFor(Integer lod) {
        List<LodLevel> ladder = policyService.getLodLadder();
        if (ladder == null || ladder.isEmpty()) return List.of();
        if (lod == null) return ladder;
        return ladder.stream().filter(l -> l.lod() == lod).toList();
    }

    private void deleteScope(Integer lod) {
        if (lod == null) {
            jdbcTemplate.update(DELETE_ALL_SQL);
            jdbcTemplate.update(DELETE_BORDER_ALL_SQL);
        } else {
            jdbcTemplate.update(DELETE_LOD_SQL, lod);
            jdbcTemplate.update(DELETE_BORDER_LOD_SQL, lod);
        }
    }

    private int allUnitsCount() {
        Long count = jdbcTemplate.queryForObject(ALL_UNITS_COUNT_SQL, Long.class);
        return count != null ? count.intValue() : 0;
    }

    private ValidationResult validate(UUID generationId) {
        return jdbcTemplate.queryForObject(VALIDATE_SQL,
                (rs, rowNum) -> new ValidationResult(
                        rs.getInt("total_rows"),
                        rs.getInt("null_count"),
                        rs.getInt("invalid_count"),
                        rs.getInt("empty_count")),
                generationId, generationId);
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
                              int totalUnits, int totalLods) {
        public static WriteResult empty() {
            return new WriteResult(0, 0, 0, 0, 0);
        }
    }

    record ValidationResult(int totalRows, int nullCount, int invalidCount, int emptyCount) {}

    public static class GenerationFailedException extends RuntimeException {
        public GenerationFailedException(String message) {
            super(message);
        }
    }
}
