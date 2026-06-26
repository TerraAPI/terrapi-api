package pt.terrapi.account.security;

import java.util.UUID;

/**
 * Authenticated principal for opaque API-key requests. Carries the resolved
 * organization so downstream code can scope and attribute work to the tenant.
 */
public record ApiKeyPrincipal(UUID organizationId, UUID apiKeyId, String label) {

    @Override
    public String toString() {
        return "api-key:" + apiKeyId;
    }
}
