package pt.terrapi.terrapi_api.service.precision;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;
import pt.terrapi.terrapi_api.service.precision.PrecisionWriter.WriteResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates precision generation: resolves the target types, delegates the atomic
 * delete+insert+validate to {@link PrecisionWriter} (which rolls back on failure), and records
 * the run in {@code precision_generations} regardless of outcome.
 */
@Slf4j
@Service
public class PrecisionGenerationService {

    private final PrecisionWriter writer;
    private final PrecisionGenerationRepository generationRepository;

    public PrecisionGenerationService(PrecisionWriter writer,
                                      PrecisionGenerationRepository generationRepository) {
        this.writer = writer;
        this.generationRepository = generationRepository;
    }

    public GenerationResult generate(GenerationType generationType) {
        return generate(generationType, null);
    }

    public GenerationResult generate(GenerationType generationType, Integer lod) {
        long t0 = System.currentTimeMillis();
        UUID generationId = UUID.randomUUID();
        List<GeoUnitType> types = resolveTypes(generationType);

        log.info("Generating precision (type={}, lod={}, genId={})", generationType, lod, generationId);

        try {
            WriteResult result = writer.write(generationId, types, lod);
            GenerationStatus status = result.degraded() ? GenerationStatus.DEGRADED : GenerationStatus.SUCCESS;
            saveAudit(generationId, generationType, status, result);
            log.info("Generation {} {} — {} rows in {} ms",
                    generationId, status, result.rowCount(), System.currentTimeMillis() - t0);
            return new GenerationResult(generationId, status, result.rowCount());
        } catch (Exception e) {
            log.error("Generation {} FAILED (rolled back)", generationId, e);
            saveAudit(generationId, generationType, GenerationStatus.FAILED, WriteResult.empty());
            return new GenerationResult(generationId, GenerationStatus.FAILED, 0);
        }
    }

    private void saveAudit(UUID generationId, GenerationType type,
                           GenerationStatus status, WriteResult result) {
        PrecisionGeneration gen = new PrecisionGeneration();
        gen.setGenerationId(generationId);
        gen.setCreatedAt(Instant.now());
        gen.setStatus(status);
        gen.setType(type);
        gen.updateCounters(result.rowCount(), result.nullCount(), result.invalidCount(),
                result.totalUnits(), result.totalLods());
        generationRepository.save(gen);
    }

    private static List<GeoUnitType> resolveTypes(GenerationType generationType) {
        if (generationType == GenerationType.ALL) {
            return List.of(GeoUnitType.values());
        }
        return List.of(GeoUnitType.valueOf(generationType.name()));
    }
}
