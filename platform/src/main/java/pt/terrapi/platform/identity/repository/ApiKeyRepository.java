package pt.terrapi.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.platform.identity.entities.ApiKey;
import pt.terrapi.platform.identity.enums.ApiKeyStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);

    @Transactional("platformTransactionManager")
    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :now where k.id = :id")
    void updateLastUsedAt(@Param("id") UUID id, @Param("now") Instant now);
}
