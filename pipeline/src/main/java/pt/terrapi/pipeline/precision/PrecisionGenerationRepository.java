package pt.terrapi.pipeline.precision;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.pipeline.precision.PrecisionGeneration;

import java.util.Optional;
import java.util.UUID;

public interface PrecisionGenerationRepository extends JpaRepository<PrecisionGeneration, UUID> {

    /** The most recent generation run (any status), for surfacing precision health. */
    Optional<PrecisionGeneration> findTopByOrderByCreatedAtDesc();
}
