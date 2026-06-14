package com.social.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter.
 *
 * For every request:
 *  1. If no Bearer token is present, pass through (Spring Security's access
 *     rules decide whether the path is public or requires authentication).
 *  2. If a Bearer token is present but invalid / expired → immediately write
 *     a 401 JSON response and stop the chain.
 *  3. If valid → populate {@link AuthContext} (ThreadLocal) and Spring's
 *     SecurityContextHolder so both our own interceptors and Spring Security's
 *     access-decision machinery see the authenticated user.
 *
 * The ThreadLocal is cleared in a finally block after the request completes.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token present — let Spring Security enforce path-level rules.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        AuthUser authUser;
        try {
            authUser = AuthUser.getAuthUser(token);
        } catch (Exception e) {
            authUser = null;
        }

        if (authUser == null) {
            writeJson(response, 401, "token expired or invalid");
            return;
        }

        // ── Tenant scope enforcement ─────────────────────────────────────────
        // Pre-auth tokens (tenantId == null, not SUPER_ADMIN) may only reach
        // the org-selection helpers. All other protected paths are blocked.
        if (!authUser.isSuperAdmin() && authUser.getTenantId() == null) {
            String path = request.getRequestURI();
            boolean isOrgSelectionPath =
                    "/auth/switch-tenant".equals(path) ||
                    "/api/tenant/register".equals(path) ||
                    "/api/email-verify/resend".equals(path);
            if (!isOrgSelectionPath) {
                writeJson(response, 403, "org selection required — call /auth/switch-tenant");
                return;
            }
        }

        // ── Populate security contexts ────────────────────────────────────────
        List<SimpleGrantedAuthority> authorities = authUser.getRoles() == null
                ? List.of()
                : authUser.getRoles().stream()
                          .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName().toUpperCase()))
                          .toList();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(authUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.set(authUser);
        LOG.debug("Authenticated user={} tenant={}", authUser.getEmail(), authUser.getTenantId());

        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    static void writeJson(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                MAPPER.writeValueAsString(new ApiResponse(false, message, null, status)));
    }
}
