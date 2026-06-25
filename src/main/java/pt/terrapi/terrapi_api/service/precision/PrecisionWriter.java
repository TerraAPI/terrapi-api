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
 * Transactional writer for {@code geo_unit_precisions}, with one shared topology across all layers.
 *
 * <p>Per LOD the <em>parish</em> coverage (the finest layer) is coverage-simplified once with
 * {@code ST_CoverageSimplify} at the shared tolerance, into a session temp table that also carries
 * each parish's municipality / district-or-island / NUTS codes. Parish precisions are inserted
 * directly; every coarser unit is then built by <em>dissolving</em> those same simplified parishes
 * ({@code ST_CoverageUnion}) grouped by the relevant code, in a single statement. Because all
 * features are composed of the same simplified parish edges, they nest exactly (a parish boundary
 * coincides with its municipality / district / NUTS boundary at every LOD).
 *
 * <p>The classified border line network ({@code border_segments}) is simplified independently per
 * arc ({@code ST_SimplifyPreserveTopology}) at the same tolerance — it carries edge-level semantics
 * not derivable from the polygon dissolve.
 *
 * <p>Generation is always the full hierarchy; only the LOD scope can be narrowed. The CAOP parish
 * coverage is authoritative and valid; if it ever isn't, generation fails loudly and rolls back.
 */
@Slf4j
@Service
public class PrecisionWriter {

    /**
     * Simplify the parish coverage once at the given tolerance and carry up the hierarchy codes.
     * Args: 1=tolerance (m), 2=simplifyBoundary, 3=PARISH type value.
     */
    private static final String CREATE_TMP_SIMP_SQL = """
            CREATE TEMP TABLE tmp_simp ON COMMIT DROP AS
            WITH simp AS (
                SELECT p.code,
                       ST_SetSRID(ST_CoverageSimplify(p.geometry_3763, %.1f, %s) OVER (), 3763) AS geom
                FROM geo_units p
                WHERE p.type = %d
                  AND p.geometry_3763 IS NOT NULL
                  AND NOT ST_IsEmpty(p.geometry_3763)
            )
            SELECT s.code AS parish_code, s.geom,
                   p.parent_code AS muni_code,
                   m.parent_code  AS dist_code,
                   p.nuts3_code   AS nuts3_code,
                   n3.parent_code AS nuts2_code,
                   n2.parent_code AS nuts1_code
            FROM simp s
            JOIN geo_units p  ON p.code  = s.code
            LEFT JOIN geo_units m  ON m.code  = p.parent_code
            LEFT JOIN geo_units n3 ON n3.code = p.nuts3_code
            LEFT JOIN geo_units n2 ON n2.code = n3.parent_code
            """;

    /** Insert the simplified parishes themselves. Args bound: lod, tolerance, generationId. */
    private static final String INSERT_PARISH_SQL = """
            INSERT INTO geo_unit_precisions
                (geo_unit_code, type, lod, geometry, tolerance_m, vertex_count,
                 generation_id, created_at)
            SELECT parish_code, %d, ?, ST_Transform(geom, 4326), ?, ST_NPoints(geom), ?, NOW()
            FROM tmp_simp
            """.formatted(GeoUnitType.PARISH.getValue());

    /**
     * Build every coarser unit in one statement: unpivot each parish's ancestor codes
     * (municipality / district-or-island / NUTS3 / NUTS2 / NUTS1), then dissolve the simplified
     * parishes per code with {@code ST_CoverageUnion}. Each unit's type is read from
     * {@code geo_units}. Args bound: lod, tolerance, generationId.
     */
    private static final String INSERT_COARSER_SQL = """
            INSERT INTO geo_unit_precisions
                (geo_unit_code, type, lod, geometry, tolerance_m, vertex_count,
                 generation_id, created_at)
            SELECT gu.code, gu.type, ?, ST_Transform(u.g, 4326), ?, ST_NPoints(u.g), ?, NOW()
            FROM (
                SELECT code, ST_SetSRID(ST_CoverageUnion(geom), 3763) AS g
                FROM (
                    SELECT muni_code  AS code, geom FROM tmp_simp WHERE muni_code  IS NOT NULL
                    UNION ALL SELECT dist_code,  geom FROM tmp_simp WHERE dist_code  IS NOT NULL
                    UNION ALL SELECT nuts3_code, geom FROM tmp_simp WHERE nuts3_code IS NOT NULL
                    UNION ALL SELECT nuts2_code, geom FROM tmp_simp WHERE nuts2_code IS NOT NULL
                    UNION ALL SELECT nuts1_code, geom FROM tmp_simp WHERE nuts1_code IS NOT NULL
                ) up
                GROUP BY code
            ) u
            JOIN geo_units gu ON gu.code = u.code
            """;

    /**
     * Independently line-simplify each classified border arc per LOD ({@code geometry_3763} is the
     * pre-projected source). Arc endpoints (shared network nodes) are preserved.
     */
    private static final String INSERT_BORDER_SQL = """
            INSERT INTO border_segment_precisions
                (border_segment_id, level, line_type, length_km, lod, geometry,
                 tolerance_m, vertex_count, generation_id, created_at)
            SELECT id, level, line_type, length_km, ?, ST_Transform(simplified_3763, 4326),
                   ?, ST_NPoints(simplified_3763), ?, NOW()
            FROM (
                SELECT b.id, b.level, b.line_type, b.length_km,
                       ST_SimplifyPreserveTopology(b.geometry_3763, ?) AS simplified_3763
                FROM border_segments b
                WHERE b.geometry_3763 IS NOT NULL AND NOT ST_IsEmpty(b.geometry_3763)
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
     * Rebuilds all precision layers (the full topology-preserving hierarchy) at every LOD, or a
     * single LOD when given, in one transaction. Throws {@link GenerationFailedException} on an
     * unhealthy result, rolling back.
     */
    @Transactional
    public WriteResult write(UUID generationId, Integer lod) {
        List<LodLevel> ladder = ladderFor(lod);
        if (ladder.isEmpty()) {
            return WriteResult.empty();
        }
        int totalLods = ladder.size();

        deleteFullScope(lod);

        log.info("Precision {}: building topology-preserving hierarchy at {} LOD(s)",
                generationId, totalLods);

        for (LodLevel level : ladder) {
            long levelStart = System.currentTimeMillis();
            materializeSimplifiedParishes(level);
            insertParish(level, generationId);
            insertCoarserLayers(level, generationId);
            insertBorders(level, generationId);
            log.info("  LOD {} @ {} m done in {} ms",
                    level.lod(), fmtTol(level.tolerance()), System.currentTimeMillis() - levelStart);
        }

        ValidationResult validation = validate(generationId);
        int totalUnits = allUnitsCount();
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

    /** Simplify the parish coverage once for this LOD into {@code tmp_simp}. */
    private void materializeSimplifiedParishes(LodLevel level) {
        String sql = String.format(Locale.US, CREATE_TMP_SIMP_SQL,
                level.tolerance(),
                policyService.isSimplifyBoundary() ? "true" : "false",
                GeoUnitType.PARISH.getValue());
        long t0 = System.currentTimeMillis();
        jdbcTemplate.execute("DROP TABLE IF EXISTS tmp_simp");
        jdbcTemplate.execute(sql);
        Integer parishes = jdbcTemplate.queryForObject("SELECT count(*) FROM tmp_simp", Integer.class);
        log.info("    simplified {} parishes @ {} m in {} ms",
                parishes, fmtTol(level.tolerance()), System.currentTimeMillis() - t0);
    }

    private void insertParish(LodLevel level, UUID generationId) {
        long t0 = System.currentTimeMillis();
        int rows = jdbcTemplate.update(INSERT_PARISH_SQL, level.lod(), level.tolerance(), generationId);
        log.info("    PARISH @ {} m -> {} rows in {} ms",
                fmtTol(level.tolerance()), rows, System.currentTimeMillis() - t0);
    }

    /** Build municipality, district/island and all NUTS levels by dissolving parishes (one query). */
    private void insertCoarserLayers(LodLevel level, UUID generationId) {
        long t0 = System.currentTimeMillis();
        int rows = jdbcTemplate.update(INSERT_COARSER_SQL, level.lod(), level.tolerance(), generationId);
        log.info("    coarser layers (dissolve) @ {} m -> {} rows in {} ms",
                fmtTol(level.tolerance()), rows, System.currentTimeMillis() - t0);
    }

    private void insertBorders(LodLevel level, UUID generationId) {
        long t0 = System.currentTimeMillis();
        int rows = jdbcTemplate.update(INSERT_BORDER_SQL,
                level.lod(), level.tolerance(), generationId, level.tolerance());
        log.info("    borders @ {} m -> {} rows in {} ms",
                fmtTol(level.tolerance()), rows, System.currentTimeMillis() - t0);
    }

    private static String fmtTol(double tolerance) {
        return String.format(Locale.US, "%.0f", tolerance);
    }

    private List<LodLevel> ladderFor(Integer lod) {
        List<LodLevel> ladder = policyService.getLodLadder();
        if (ladder == null || ladder.isEmpty()) return List.of();
        if (lod == null) return ladder;
        return ladder.stream().filter(l -> l.lod() == lod).toList();
    }

    private void deleteFullScope(Integer lod) {
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
