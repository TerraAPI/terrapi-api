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
import java.util.stream.Collectors;

/**
 * Transactional writer for {@code geo_unit_precisions}. Generation is hierarchical: the parish
 * coverage is coverage-simplified per LOD, then coarser layers are derived by dissolving the
 * already-simplified children ({@code ST_CoverageUnion}). All layers therefore share one edge
 * graph (parish ⊂ municipality ⊂ district; NUTS borders follow municipality borders). The CAOP
 * data is an authoritative, valid coverage; if it ever isn't, generation fails loudly and rolls
 * back rather than degrading.
 */
@Slf4j
@Service
public class PrecisionWriter {

    private static final int PARISH = GeoUnitType.PARISH.getValue();
    private static final int MUNICIPALITY = GeoUnitType.MUNICIPALITY.getValue();
    private static final int NUTS3 = GeoUnitType.NUTS3.getValue();
    private static final int NUTS2 = GeoUnitType.NUTS2.getValue();
    private static final int NUTS1 = GeoUnitType.NUTS1.getValue();

    private static final String INSERT_PARISH_SQL = """
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

    /** Dissolve a source layer's precisions into a parent layer with a fixed target type. */
    private static final String DISSOLVE_BY_COLUMN_SQL = """
            INSERT INTO geo_unit_precisions
                (geo_unit_code, type, lod, geometry, tolerance_m, vertex_count, generation_id, created_at)
            SELECT code, %d, %d, geom, %.1f, ST_NPoints(geom), ?, NOW()
            FROM (
                SELECT %s AS code, ST_CoverageUnion(gp.geometry) AS geom
                FROM geo_unit_precisions gp
                JOIN geo_units u ON u.code = gp.geo_unit_code
                WHERE gp.type = %d AND gp.lod = %d AND gp.generation_id = ?
                  AND %s IS NOT NULL
                GROUP BY %s
            ) d
            """;

    /** Dissolve municipalities into their parent, taking the target type from the parent unit. */
    private static final String DISSOLVE_TO_PARENT_TYPE_SQL = """
            INSERT INTO geo_unit_precisions
                (geo_unit_code, type, lod, geometry, tolerance_m, vertex_count, generation_id, created_at)
            SELECT code, gtype, %d, geom, %.1f, ST_NPoints(geom), ?, NOW()
            FROM (
                SELECT d.code AS code, d.type AS gtype, ST_CoverageUnion(gp.geometry) AS geom
                FROM geo_unit_precisions gp
                JOIN geo_units u ON u.code = gp.geo_unit_code
                JOIN geo_units d ON d.code = u.parent_code
                WHERE gp.type = %d AND gp.lod = %d AND gp.generation_id = ?
                GROUP BY d.code, d.type
            ) x
            """;

    private static final String ALL_UNITS_COUNT_SQL =
            "SELECT COUNT(*) FROM geo_units WHERE geometry IS NOT NULL AND NOT ST_IsEmpty(geometry)";

    private static final String DELETE_ALL_SQL = "DELETE FROM geo_unit_precisions";

    private static final String DELETE_LOD_SQL = "DELETE FROM geo_unit_precisions WHERE lod = ?";

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
     * Rebuilds the nested hierarchy for all LODs, or a single LOD when given, in one transaction.
     * Throws {@link GenerationFailedException} on an unhealthy result, rolling back.
     */
    @Transactional
    public WriteResult write(UUID generationId, Integer lod) {
        List<LodLevel> ladder = ladderFor(lod);
        if (ladder.isEmpty()) {
            return WriteResult.empty();
        }

        deleteScope(lod);
        for (LodLevel level : ladder) {
            buildHierarchy(level, generationId);
        }
        log.info("  Generated nested hierarchy for {} LOD level(s)", ladder.size());

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

    private void buildHierarchy(LodLevel level, UUID generationId) {
        int lod = level.lod();
        double t = level.tolerance();
        insertParishBase(level, generationId);
        dissolveByColumn(MUNICIPALITY, PARISH, lod, t, "u.parent_code", generationId);
        dissolveToParentType(MUNICIPALITY, lod, t, generationId);
        dissolveByColumn(NUTS3, MUNICIPALITY, lod, t, "u.nuts3_code", generationId);
        dissolveByColumn(NUTS2, NUTS3, lod, t, "u.parent_code", generationId);
        dissolveByColumn(NUTS1, NUTS2, lod, t, "u.parent_code", generationId);
    }

    private void insertParishBase(LodLevel level, UUID generationId) {
        String valuesClause = String.format(Locale.US, "(%d, %.1f)", level.lod(), level.tolerance());
        String sql = String.format(INSERT_PARISH_SQL, valuesClause,
                policyService.isSimplifyBoundary() ? "true" : "false");
        jdbcTemplate.update(sql, PARISH, PARISH, generationId);
    }

    private void dissolveByColumn(int targetType, int sourceType, int lod, double tolerance,
                                  String groupColumn, UUID generationId) {
        String sql = String.format(Locale.US, DISSOLVE_BY_COLUMN_SQL,
                targetType, lod, tolerance, groupColumn, sourceType, lod, groupColumn, groupColumn);
        jdbcTemplate.update(sql, generationId, generationId);
    }

    private void dissolveToParentType(int sourceType, int lod, double tolerance, UUID generationId) {
        String sql = String.format(Locale.US, DISSOLVE_TO_PARENT_TYPE_SQL,
                lod, tolerance, sourceType, lod);
        jdbcTemplate.update(sql, generationId, generationId);
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
        } else {
            jdbcTemplate.update(DELETE_LOD_SQL, lod);
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
