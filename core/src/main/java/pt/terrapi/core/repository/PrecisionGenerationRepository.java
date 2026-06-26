package pt.terrapi.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;

import java.util.Optional;
import java.util.UUID;

public interface PrecisionGenerationRepository extends JpaRepository<PrecisionGeneration, UUID> {

    /** The most recent generation run (any status), for surfacing precision health. */
    Optional<PrecisionGeneration> findTopByOrderByCreatedAtDesc();
}
