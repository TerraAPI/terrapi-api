package pt.terrapi.terrapi_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.GeoUnitPrecisionRepository;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PrecisionGenerationService {

    private static final String INSERT_LODS_SQL = """
            WITH lods(lod, tolerance) AS (
                VALUES %s
            ),
            base AS (
                SELECT u.code,
                       ST_Transform(u.geometry, 3763) AS geom_3763
                FROM geo_units u
                WHERE u.type = ?
            ),
            simplified AS (
                SELECT b.code, l.lod, l.tolerance,
                       ST_SimplifyPreserveTopology(b.geom_3763, l.tolerance) AS simplified_3763
                FROM base b
                CROSS JOIN lods l
            )
            INSERT INTO geo_unit_precisions
                (geo_unit_code, type, lod, geometry, tolerance_m, vertex_count,
                 generation_id, created_at, status)
            SELECT code, ?, lod, ST_Transform(simplified_3763, 4326), tolerance,
                   ST_NPoints(simplified_3763), ?, NOW(), 'ACTIVE'
            FROM simplified
            """;

    private static final String VALIDATE_SQL = """
            SELECT COALESCE(COUNT(*), 0) AS total_rows,
                   COALESCE(COUNT(*) FILTER (WHERE geometry IS NULL), 0) AS null_count,
                   COALESCE(COUNT(*) FILTER (WHERE NOT ST_IsValid(geometry)), 0) AS invalid_count,
                   COALESCE(COUNT(*) FILTER (WHERE ST_IsEmpty(geometry)), 0) AS empty_count
            FROM geo_unit_precisions
            WHERE generation_id = ?
            """;

    private static final String DELETE_GENERATION_SQL =
            "DELETE FROM geo_unit_precisions WHERE generation_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final PrecisionPolicyService policyService;
    private final PrecisionGenerationRepository generationRepository;
    private final GeoUnitPrecisionRepository precisionRepository;
    private final PrecisionProperties properties;

    public PrecisionGenerationService(JdbcTemplate jdbcTemplate,
                                      PrecisionPolicyService policyService,
                                      PrecisionGenerationRepository generationRepository,
                                      GeoUnitPrecisionRepository precisionRepository,
                                      PrecisionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.policyService = policyService;
        this.generationRepository = generationRepository;
        this.precisionRepository = precisionRepository;
        this.properties = properties;
    }

    @Transactional
    public GenerationResult generate(GenerationType generationType) {
        long t0 = System.currentTimeMillis();
        UUID generationId = UUID.randomUUID();

        PrecisionGeneration gen = new PrecisionGeneration();
        gen.setGenerationId(generationId);
        gen.setCreatedAt(Instant.now());
        gen.setStatus(GenerationStatus.RUNNING);
        gen.setType(generationType);
        generationRepository.saveAndFlush(gen);

        log.info("Generating precision (type={}, genId={})", generationType, generationId);

        try {
            return doGenerate(generationId, gen, generationType, t0);
        } catch (Exception e) {
            log.error("Generation {} crashed", generationId, e);
            gen.setStatus(GenerationStatus.FAILED);
            gen.setRowCount(0);
            generationRepository.save(gen);
            return new GenerationResult(generationId, GenerationStatus.FAILED, 0);
        }
    }

    private GenerationResult doGenerate(UUID generationId, PrecisionGeneration gen,
                                        GenerationType generationType, long t0) {
        List<GeoUnitType> targetTypes = resolveTypes(generationType);

        int totalLodDefs = 0;
        long expectedRowCount = 0;

        log.info("  Counting units...");
        for (GeoUnitType type : targetTypes) {
            List<LodLevel> levels = policyService.getLodLevels(type);
            if (levels.isEmpty()) continue;
            totalLodDefs += levels.size();
            Long unitCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM geo_units WHERE type = ?",
                    Long.class, type.getValue());
            long count = unitCount != null ? unitCount : 0;
            expectedRowCount += count * levels.size();
        }

        log.info("  Deprecating old rows...");
        precisionRepository.deprecateActiveByTypes(targetTypes);
        precisionRepository.flush();

        log.info("  Computing LODs...");
        int totalRows = 0;
        for (GeoUnitType type : targetTypes) {
            List<LodLevel> levels = policyService.getLodLevels(type);
            if (levels.isEmpty()) continue;
            long tType = System.currentTimeMillis();
            int rows = insertLods(type, levels, generationId);
            totalRows += rows;
            log.info("    {}: {} rows ({} ms)", type, rows, System.currentTimeMillis() - tType);
        }

        ValidationResult validation = validateGeneration(generationId);
        int totalUnits = totalLodDefs > 0 ? (int) expectedRowCount / totalLodDefs : 0;

        if (validation == null) {
            log.warn("Generation {} produced no rows (no LOD levels configured?)", generationId);
            gen.setStatus(GenerationStatus.SUCCESS);
            gen.updateCounters(0, 0, 0, 0, 0);
            generationRepository.save(gen);
            return new GenerationResult(generationId, GenerationStatus.SUCCESS, 0);
        }

        if (!validation.isHealthy(properties, expectedRowCount)) {
            log.error("Generation {} FAILED — rows={} expected={} null={}% invalid={}% empty={}%",
                    generationId, validation.totalRows, expectedRowCount,
                    validation.nullPct(), validation.invalidPct(), validation.emptyPct());
            gen.setStatus(GenerationStatus.FAILED);
            gen.updateCounters(validation.totalRows, validation.nullCount, validation.invalidCount,
                    totalUnits, totalLodDefs);
            generationRepository.save(gen);
            jdbcTemplate.update(DELETE_GENERATION_SQL, generationId);
            return new GenerationResult(generationId, GenerationStatus.FAILED, validation.totalRows);
        }

        gen.setStatus(GenerationStatus.SUCCESS);
        gen.updateCounters(validation.totalRows, validation.nullCount, validation.invalidCount,
                totalUnits, totalLodDefs);
        generationRepository.save(gen);

        log.info("Generation {} SUCCESS — {} rows in {} ms",
                generationId, validation.totalRows, System.currentTimeMillis() - t0);

        return new GenerationResult(generationId, GenerationStatus.SUCCESS, validation.totalRows);
    }

    private int insertLods(GeoUnitType type, List<LodLevel> levels, UUID generationId) {
        if (levels.isEmpty()) return 0;
        String valuesClause = levels.stream()
                .map(l -> String.format("(%d, %.1f)", l.lod(), l.tolerance()))
                .collect(Collectors.joining(", "));
        String sql = String.format(INSERT_LODS_SQL, valuesClause);
        return jdbcTemplate.update(sql, type.getValue(), type.getValue(), generationId);
    }

    private ValidationResult validateGeneration(UUID generationId) {
        return jdbcTemplate.queryForObject(VALIDATE_SQL,
                (rs, rowNum) -> new ValidationResult(
                        rs.getInt("total_rows"),
                        rs.getInt("null_count"),
                        rs.getInt("invalid_count"),
                        rs.getInt("empty_count")),
                generationId);
    }

    private static List<GeoUnitType> resolveTypes(GenerationType generationType) {
        if (generationType == GenerationType.ALL) {
            return List.of(GeoUnitType.values());
        }
        return List.of(GeoUnitType.valueOf(generationType.name()));
    }

    record ValidationResult(int totalRows, int nullCount, int invalidCount, int emptyCount) {

        double nullPct() {
            return totalRows > 0 ? 100.0 * nullCount / totalRows : 0;
        }

        double invalidPct() {
            return totalRows > 0 ? 100.0 * invalidCount / totalRows : 0;
        }

        double emptyPct() {
            return totalRows > 0 ? 100.0 * emptyCount / totalRows : 0;
        }

        boolean isHealthy(PrecisionProperties properties, long expectedRowCount) {
            PrecisionProperties.Validation thresholds = properties.getValidation();
            return totalRows == expectedRowCount
                    && nullPct() <= thresholds.getMaxNullPct()
                    && invalidPct() <= thresholds.getMaxInvalidPct()
                    && emptyPct() <= thresholds.getMaxEmptyPct();
        }
    }
}
