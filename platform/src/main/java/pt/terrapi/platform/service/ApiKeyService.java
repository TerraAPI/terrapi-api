package pt.terrapi.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.platform.config.AccountProperties;
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
    private final AccountProperties properties;

    private final Cache<UUID, Boolean> lastUsedThrottle = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, AccountProperties properties) {
        this.apiKeyRepository = apiKeyRepository;
        this.properties = properties;
    }

    /** Result of creating a key: the full plaintext is returned once and never stored. */
    public record CreatedApiKey(String plaintext, ApiKey apiKey) {
    }

    @Transactional("accountTransactionManager")
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
     * Resolves an opaque token to an active, unexpired {@link ApiKey}, refreshing
     * {@code lastUsedAt} at most once per throttle window.
     */
    @Transactional("accountTransactionManager")
    public Optional<ApiKey> resolve(String token) {
        Optional<ApiKey> match = apiKeyRepository.findByKeyHashAndStatus(sha256Hex(token), ApiKeyStatus.ACTIVE);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        ApiKey apiKey = match.get();
        Instant now = Instant.now();
        if (apiKey.getExpiresAt() != null && now.isAfter(apiKey.getExpiresAt())) {
            return Optional.empty();
        }
        if (lastUsedThrottle.getIfPresent(apiKey.getId()) == null) {
            apiKey.setLastUsedAt(now);
            lastUsedThrottle.put(apiKey.getId(), Boolean.TRUE);
        }
        return Optional.of(apiKey);
    }

    @Transactional("accountTransactionManager")
    public void revoke(ApiKey apiKey) {
        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
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
