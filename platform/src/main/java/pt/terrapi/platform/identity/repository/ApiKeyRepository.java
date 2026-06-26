package pt.terrapi.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.platform.identity.entities.ApiKey;
import pt.terrapi.platform.identity.enums.ApiKeyStatus;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);
}
