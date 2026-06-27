package pt.terrapi.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.platform.config.PlatformProperties;
import pt.terrapi.platform.entities.ApiKey;
import pt.terrapi.platform.entities.Organization;
import pt.terrapi.platform.enums.ApiKeyStatus;
import pt.terrapi.platform.repository.ApiKeyRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    /** Marker shared by all opaque keys (e.g. {@code tp_live_}, {@code tp_test_}). */
    public static final String TOKEN_MARKER = "tp_";

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final ApiKeyRepository apiKeyRepository;
    private final PlatformProperties properties;

    private final Cache<UUID, Boolean> lastUsedThrottle = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    /**
     * Caches token-hash -> resolution (positives and negatives) for a short window so the
     * data API does not hit the platform DB on every request. Cross-instance revocation lag
     * is bounded by this TTL; {@link #revoke} invalidates locally on the spot.
     */
    private final Cache<String, Optional<ResolvedApiKey>> resolveCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(10_000)
            .build();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, PlatformProperties properties) {
        this.apiKeyRepository = apiKeyRepository;
        this.properties = properties;
    }

    /** Result of creating a key: the full plaintext is returned once and never stored. */
    public record CreatedApiKey(String plaintext, ApiKey apiKey) {
    }

    /** Immutable, cacheable snapshot of a resolved key (no JPA/session coupling). */
    public record ResolvedApiKey(UUID apiKeyId, UUID organizationId, String label, Instant expiresAt) {
    }

    @Transactional("platformTransactionManager")
    public CreatedApiKey create(Organization organization, String label, UUID createdBy, Instant expiresAt) {
        byte[] raw = new byte[SECRET_BYTES];
        RANDOM.nextBytes(raw);
        String secret = BASE64.encodeToString(raw);
        String plaintext = properties.apiKeyPrefix() + secret;

        ApiKey apiKey = new ApiKey();
        apiKey.setOrganization(organization);
        apiKey.setLabel(label);
        apiKey.setPrefix(properties.apiKeyPrefix() + secret.substring(0, 6));
        apiKey.setLastFour(secret.substring(secret.length() - 4));
        apiKey.setKeyHash(sha256Hex(plaintext));
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setCreatedBy(createdBy);
        apiKey.setExpiresAt(expiresAt);

        return new CreatedApiKey(plaintext, apiKeyRepository.save(apiKey));
    }

    /**
     * Resolves an opaque token to an active, unexpired key snapshot. Served from a short-TTL
     * cache (positives and negatives) to avoid a platform-DB read per request; refreshes
     * {@code lastUsedAt} at most once per throttle window.
     */
    public Optional<ResolvedApiKey> resolve(String token) {
        String hash = sha256Hex(token);
        Optional<ResolvedApiKey> resolved = resolveCache.get(hash, this::loadSnapshot);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        ResolvedApiKey snapshot = resolved.get();
        if (snapshot.expiresAt() != null && Instant.now().isAfter(snapshot.expiresAt())) {
            resolveCache.invalidate(hash);
            return Optional.empty();
        }
        touchLastUsed(snapshot.apiKeyId());
        return resolved;
    }

    private Optional<ResolvedApiKey> loadSnapshot(String keyHash) {
        return apiKeyRepository.findByKeyHashAndStatus(keyHash, ApiKeyStatus.ACTIVE)
                .map(key -> new ResolvedApiKey(
                        key.getId(), key.getOrganization().getId(), key.getLabel(), key.getExpiresAt()));
    }

    private void touchLastUsed(UUID apiKeyId) {
        if (lastUsedThrottle.getIfPresent(apiKeyId) == null) {
            lastUsedThrottle.put(apiKeyId, Boolean.TRUE);
            apiKeyRepository.updateLastUsedAt(apiKeyId, Instant.now());
        }
    }

    @Transactional("platformTransactionManager")
    public void revoke(ApiKey apiKey) {
        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
        resolveCache.invalidate(apiKey.getKeyHash());
    }

    public boolean isApiKeyToken(String token) {
        return token != null && token.startsWith(TOKEN_MARKER);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
