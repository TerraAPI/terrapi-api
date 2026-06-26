package pt.terrapi.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.platform.entities.ApiKey;
import pt.terrapi.platform.enums.ApiKeyStatus;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);
}
