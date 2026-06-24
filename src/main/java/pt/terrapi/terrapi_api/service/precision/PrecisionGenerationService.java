package pt.terrapi.terrapi_api.service.precision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;
import pt.terrapi.terrapi_api.service.precision.PrecisionWriter.WriteResult;

import java.util.UUID;

/**
 * Orchestrates precision generation: delegates the atomic delete+insert+validate of
 * per-layer precisions to {@link PrecisionWriter} (which rolls back on failure) and records
 * the run in {@code precision_generations} regardless of outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrecisionGenerationService {

    private final PrecisionWriter writer;
    private final PrecisionGenerationRepository generationRepository;

    public GenerationResult generate() {
        return generate(null, null);
    }

    public GenerationResult generate(Integer lod) {
        return generate(lod, null);
    }

    public GenerationResult generate(Integer lod, GeoUnitType type) {
        long t0 = System.currentTimeMillis();

        PrecisionGeneration gen = new PrecisionGeneration();
        gen.setStatus(GenerationStatus.RUNNING);
        gen = generationRepository.save(gen);
        UUID generationId = gen.getGenerationId();

        log.info("Generating precision (lod={}, type={}, genId={})", lod, type, generationId);

        try {
            WriteResult result = writer.write(generationId, lod, type);
            gen.setStatus(GenerationStatus.SUCCESS);
            gen.updateCounters(result.rowCount(), result.nullCount(), result.invalidCount(),
                    result.totalUnits(), result.totalLods());
            generationRepository.save(gen);
            log.info("Generation {} SUCCESS — {} rows in {} ms",
                    generationId, result.rowCount(), System.currentTimeMillis() - t0);
            return new GenerationResult(generationId, GenerationStatus.SUCCESS, result.rowCount());
        } catch (Exception e) {
            log.error("Generation {} FAILED (rolled back)", generationId, e);
            gen.setStatus(GenerationStatus.FAILED);
            generationRepository.save(gen);
            return new GenerationResult(generationId, GenerationStatus.FAILED, 0);
        }
    }
}
