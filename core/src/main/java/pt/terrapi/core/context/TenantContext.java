package pt.terrapi.core.context;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local holder for the organization (tenant) scoped to the current request.
 *
 * <p>Lives in {@code core} so {@code web}'s data services can scope and attribute usage
 * without a compile-time dependency on {@code account}. It is populated by the security
 * layer (API-key authentication) and must always be cleared at the end of the request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> ORGANIZATION_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setOrganizationId(UUID organizationId) {
        ORGANIZATION_ID.set(organizationId);
    }

    public static Optional<UUID> getOrganizationId() {
        return Optional.ofNullable(ORGANIZATION_ID.get());
    }

    public static UUID requireOrganizationId() {
        UUID organizationId = ORGANIZATION_ID.get();
        if (organizationId == null) {
            throw new IllegalStateException("No organization bound to the current request");
        }
        return organizationId;
    }

    public static void clear() {
        ORGANIZATION_ID.remove();
    }
}
