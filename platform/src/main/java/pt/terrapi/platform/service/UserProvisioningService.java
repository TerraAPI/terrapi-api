package pt.terrapi.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.platform.entities.AppUser;
import pt.terrapi.platform.repository.AppUserRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Just-in-time provisioning: mirrors a Keycloak user into {@code app_users} on first
 * sight and refreshes {@code last_seen_at}. Keycloak owns credentials; the app owns
 * org membership, roles and usage.
 */
@Service
public class UserProvisioningService {

    private final AppUserRepository appUserRepository;

    private final Cache<String, Boolean> seenThrottle = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    public UserProvisioningService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional("accountTransactionManager")
    public void upsertFromJwt(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || seenThrottle.getIfPresent(sub) != null) {
            return;
        }

        String email = firstNonBlank(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                sub);
        String fullName = firstNonBlank(
                jwt.getClaimAsString("name"),
                joinNames(jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name")));

        AppUser user = appUserRepository.findByKeycloakSub(sub).orElseGet(AppUser::new);
        user.setKeycloakSub(sub);
        user.setEmail(email);
        if (fullName != null) {
            user.setFullName(fullName);
        }
        user.setLastSeenAt(Instant.now());
        appUserRepository.save(user);

        seenThrottle.put(sub, Boolean.TRUE);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String joinNames(String given, String family) {
        String joined = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        return joined.isEmpty() ? null : joined;
    }
}
