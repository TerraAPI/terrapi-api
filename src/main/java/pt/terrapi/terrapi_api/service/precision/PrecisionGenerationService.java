package pt.terrapi.terrapi_api.service.precision;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;
import pt.terrapi.terrapi_api.service.precision.PrecisionWriter.WriteResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates precision generation: delegates the atomic delete+insert+validate of the whole
 * nested hierarchy to {@link PrecisionWriter} (which rolls back on failure) and records the run in
 * {@code precision_generations} regardless of outcome.
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

        log.info("Generating precision (lod={}, genId={})", lod, generationId);

        try {
            WriteResult result = writer.write(generationId, lod);
            saveAudit(generationId, generationType, GenerationStatus.SUCCESS, result);
            log.info("Generation {} SUCCESS — {} rows in {} ms",
                    generationId, result.rowCount(), System.currentTimeMillis() - t0);
            return new GenerationResult(generationId, GenerationStatus.SUCCESS, result.rowCount());
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
}
