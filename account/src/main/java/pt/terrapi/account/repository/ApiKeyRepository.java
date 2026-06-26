package pt.terrapi.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.account.entities.ApiKey;
import pt.terrapi.account.enums.ApiKeyStatus;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);
}
