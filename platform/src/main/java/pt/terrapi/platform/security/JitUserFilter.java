package pt.terrapi.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.terrapi.platform.identity.service.UserProvisioningService;

import java.io.IOException;

/**
 * Runs after the OAuth2 bearer filter: when the request carries a valid realm JWT,
 * just-in-time provisions / refreshes the matching {@code app_users} row. Provisioning
 * failures are logged but never fail the request.
 */
@Component
public class JitUserFilter extends OncePerRequestFilter {

    private final UserProvisioningService userProvisioningService;

    public JitUserFilter(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            try {
                userProvisioningService.upsertFromJwt(jwtAuthentication.getToken());
            } catch (RuntimeException e) {
                logger.warn("JIT user provisioning failed", e);
            }
        }
        filterChain.doFilter(request, response);
    }
}
