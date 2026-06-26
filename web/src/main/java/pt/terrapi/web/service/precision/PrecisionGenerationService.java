package pt.terrapi.web.service.precision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.terrapi.core.dto.GenerationResult;
import pt.terrapi.core.entities.PrecisionGeneration;
import pt.terrapi.core.enums.GenerationStatus;
import pt.terrapi.core.repository.PrecisionGenerationRepository;
import pt.terrapi.web.service.precision.PrecisionWriter.WriteResult;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecisionGenerationService {

    private final PrecisionWriter writer;
    private final PrecisionGenerationRepository generationRepository;

    public Optional<PrecisionGeneration> latestGeneration() {
        return generationRepository.findTopByOrderByCreatedAtDesc();
    }

    public GenerationResult generate() {
        return generate(null);
    }

    public GenerationResult generate(Integer lod) {
        long t0 = System.currentTimeMillis();

        PrecisionGeneration gen = new PrecisionGeneration();
        gen.setStatus(GenerationStatus.RUNNING);
        gen = generationRepository.save(gen);
        UUID generationId = gen.getGenerationId();

        log.info("Generating precision (lod={}, genId={})", lod, generationId);

        try {
            WriteResult result = writer.write(generationId, lod);
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