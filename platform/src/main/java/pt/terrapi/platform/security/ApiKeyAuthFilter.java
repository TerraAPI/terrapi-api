package pt.terrapi.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.terrapi.platform.identity.entities.ApiKey;
import pt.terrapi.platform.identity.service.ApiKeyService;
import pt.terrapi.core.context.TenantContext;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates opaque API keys carried as {@code Authorization: Bearer tp_live_...}.
 * Registered before the OAuth2 bearer filter; the {@code BearerTokenResolver} is
 * configured to ignore API-key tokens so they never reach the JWT decoder.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || !apiKeyService.isApiKeyToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> resolved = apiKeyService.resolve(token);
        if (resolved.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        ApiKey apiKey = resolved.get();
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                apiKey.getOrganization().getId(), apiKey.getId(), apiKey.getLabel());
        ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(principal, List.of(
                new SimpleGrantedAuthority("ROLE_API_KEY"),
                new SimpleGrantedAuthority("SCOPE_data")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        TenantContext.setOrganizationId(principal.organizationId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
