package pt.terrapi.terrapi_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.ParseException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.GeoUnitPrecision;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.GeoUnitPrecisionRepository;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecisionGenerationService {

    private static final WKBReader wkbReader = new WKBReader();

    private final JdbcTemplate jdbcTemplate;
    private final PrecisionPolicyService policyService;
    private final PrecisionGenerationRepository generationRepository;
    private final GeoUnitPrecisionRepository precisionRepository;
    private final PrecisionProperties properties;

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

        List<GeoUnitPrecision> precisions = new ArrayList<>();
        long expectedRowCount = 0;
        int totalLodDefs = 0;

        log.info("  Computing LODs...");
        for (GeoUnitType type : targetTypes) {
            List<LodLevel> levels = policyService.getLodLevels(type);
            if (levels.isEmpty()) continue;

            totalLodDefs += levels.size();
            Long unitCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM geo_units WHERE type = ?",
                    Long.class, type.getValue());
            long count = unitCount != null ? unitCount : 0;
            expectedRowCount += count * levels.size();

            long tType = System.currentTimeMillis();
            for (LodLevel level : levels) {
                precisions.addAll(computeLod(type, level, generationId));
            }
            log.info("    {}: {} units \u00d7 {} LODs = {} rows ({} ms)",
                    type, count, levels.size(), count * levels.size(), System.currentTimeMillis() - tType);
        }

        int totalRows = precisions.size();
        int nullCount = 0;
        int invalidCount = 0;
        int emptyCount = 0;

        log.info("  Validating {} geometries...", totalRows);
        for (GeoUnitPrecision p : precisions) {
            Geometry g = p.getGeometry();
            if (g == null) {
                nullCount++;
            } else {
                if (!g.isValid()) invalidCount++;
                if (g.isEmpty()) emptyCount++;
            }
        }

        boolean rowCountMatches = totalRows == expectedRowCount;
        double nullPct = totalRows > 0 ? 100.0 * nullCount / totalRows : 0;
        double invalidPct = totalRows > 0 ? 100.0 * invalidCount / totalRows : 0;
        double emptyPct = totalRows > 0 ? 100.0 * emptyCount / totalRows : 0;

        double maxNull = properties.getValidation().getMaxNullPct();
        double maxInvalid = properties.getValidation().getMaxInvalidPct();
        double maxEmpty = properties.getValidation().getMaxEmptyPct();

        if (!rowCountMatches || nullPct > maxNull || invalidPct > maxInvalid || emptyPct > maxEmpty) {
            log.error("Generation {} FAILED — rows={} expected={} null={}% invalid={}% empty={}%",
                    generationId, totalRows, expectedRowCount, nullPct, invalidPct, emptyPct);
            gen.setStatus(GenerationStatus.FAILED);
            gen.setRowCount(totalRows);
            gen.setNullGeometries(nullCount);
            gen.setInvalidGeometries(invalidCount);
            gen.setTotalUnits(totalLodDefs > 0 ? (int) expectedRowCount / totalLodDefs : 0);
            gen.setTotalLods(totalLodDefs);
            generationRepository.save(gen);
            return new GenerationResult(generationId, GenerationStatus.FAILED, totalRows);
        }

        log.info("  Deprecating old rows, saving {} new rows...", totalRows);
        precisionRepository.deprecateActiveByTypes(targetTypes);
        precisionRepository.flush();
        precisionRepository.saveAll(precisions);

        gen.setStatus(GenerationStatus.SUCCESS);
        gen.setRowCount(totalRows);
        gen.setNullGeometries(nullCount);
        gen.setInvalidGeometries(invalidCount);
        gen.setTotalUnits(totalLodDefs > 0 ? (int) expectedRowCount / totalLodDefs : 0);
        gen.setTotalLods(totalLodDefs);
        generationRepository.save(gen);

        log.info("Generation {} SUCCESS — {} rows in {} ms",
                generationId, totalRows, System.currentTimeMillis() - t0);

        return new GenerationResult(generationId, GenerationStatus.SUCCESS, totalRows);
    }

    private List<GeoUnitPrecision> computeLod(GeoUnitType type, LodLevel level, UUID generationId) {
        String sql = """
                SELECT u.code,
                       ST_AsBinary(
                           ST_SetSRID(
                               ST_SimplifyPreserveTopology(ST_SetSRID(u.geometry, 3763), ?),
                               4326
                           )
                       ) AS geometry,
                       ST_NPoints(
                           ST_SimplifyPreserveTopology(ST_SetSRID(u.geometry, 3763), ?)
                       ) AS vertex_count
                FROM geo_units u
                WHERE u.type = ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            GeoUnitPrecision p = new GeoUnitPrecision();
            p.setGeoUnitCode(rs.getString("code"));
            p.setType(type);
            p.setLod(level.lod());
            p.setToleranceM(level.tolerance());
            p.setVertexCount(rs.getInt("vertex_count"));
            p.setGenerationId(generationId);
            p.setCreatedAt(Instant.now());
            p.setStatus("ACTIVE");

            byte[] wkb = rs.getBytes("geometry");
            if (wkb != null) {
                try {
                    p.setGeometry(wkbReader.read(wkb));
                } catch (ParseException e) {
                    throw new RuntimeException("Failed to parse WKB for geo unit " + p.getGeoUnitCode(), e);
                }
            }

            return p;
        }, level.tolerance(), level.tolerance(), type.getValue());
    }

    private static List<GeoUnitType> resolveTypes(GenerationType generationType) {
        if (generationType == GenerationType.ALL) {
            return List.of(GeoUnitType.values());
        }
        return List.of(GeoUnitType.valueOf(generationType.name()));
    }
}
