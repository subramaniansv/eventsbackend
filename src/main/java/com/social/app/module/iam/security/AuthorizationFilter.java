package com.social.app.module.iam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.util.SendResponseUtil;

@WebFilter(filterName = "1_AuthorizationFilter", urlPatterns = "/*")
public class AuthorizationFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationFilter.class);

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        LOG.info("filter activated");
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path       = request.getServletPath();
        String httpMethod = request.getMethod();

        // --- Public endpoints (no JWT required) ---

        // Auth: register / login / Google sign-in / token refresh.
        // Exact match only — subpaths like /auth/switch-tenant still require a token.
        if ("POST".equalsIgnoreCase(httpMethod) && "/auth".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        // Health probes
        if (path != null && (path.startsWith("/health") || path.equals("/ping"))) {
            chain.doFilter(request, response);
            return;
        }
        // Email verification link (GET — user clicks link from inbox)
        if ("GET".equalsIgnoreCase(httpMethod) && "/api/email-verify".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        // Forgot-password: request link + consume token
        if ("POST".equalsIgnoreCase(httpMethod) && path != null
                && (path.equals("/api/password-reset") || path.equals("/api/password-reset/confirm"))) {
            chain.doFilter(request, response);
            return;
        }
        // Invite acceptance link (GET — no prior session)
        if ("GET".equalsIgnoreCase(httpMethod) && "/api/tenant/invite/accept".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // --- All other requests require a valid Bearer token ---

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "missing or invalid authorization header", null, 401), response);
            return;
        }
        String token = authHeader.substring(7);

        AuthUser authUser;
        Class<?> servletClass;
        try {
            authUser = AuthUser.getAuthUser(token);
            if (authUser == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "token expired or invalid", null, 401), response);
                return;
            }

            String servletName = request.getHttpServletMapping().getServletName();
            servletClass = Class.forName(servletName);
            AuthContext.set(authUser);
            LOG.info("authed user={} tenant={}", authUser.getEmail(), authUser.getTenantId());

            // --- Tenant scope enforcement ---
            // Pre-auth tokens (tenantId == null, not SUPER_ADMIN) may only reach
            // the org-selection helpers. All other paths are blocked.
            if (!authUser.isSuperAdmin() && authUser.getTenantId() == null) {
                boolean isOrgSelectionPath =
                        "/auth/switch-tenant".equals(path) ||
                        "/api/tenant/register".equals(path) ||
                        "/api/email-verify/resend".equals(path);
                if (!isOrgSelectionPath) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "org selection required — call /auth/switch-tenant", null, 403),
                            response);
                    return;
                }
            }

            // --- Role / permission annotation checks (class level) ---
            if (servletClass.isAnnotationPresent(RequiresRole.class)) {
                RequiresRole annotation = servletClass.getAnnotation(RequiresRole.class);
                if (!checkRole(authUser, annotation)) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "access denied: insufficient role", null, 403), response);
                    return;
                }
            }
            if (servletClass.isAnnotationPresent(RequiresPermission.class)) {
                RequiresPermission annotation = servletClass.getAnnotation(RequiresPermission.class);
                if (!authUser.hasPermission(annotation.resource(), annotation.action())) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "access denied: insufficient permission", null, 403), response);
                    return;
                }
            }

            // --- Role / permission annotation checks (method level) ---
            String servletMethod = resolveServletMethod(httpMethod);
            try {
                java.lang.reflect.Method method = servletClass.getMethod(
                        servletMethod, HttpServletRequest.class, HttpServletResponse.class);

                if (method.isAnnotationPresent(RequiresRole.class)) {
                    RequiresRole annotation = method.getAnnotation(RequiresRole.class);
                    if (!checkRole(authUser, annotation)) {
                        SendResponseUtil.sendResponse(
                                new ApiResponse(false, "access denied: insufficient role on method", null, 403),
                                response);
                        return;
                    }
                }
                if (method.isAnnotationPresent(RequiresPermission.class)) {
                    RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
                    if (!authUser.hasPermission(annotation.resource(), annotation.action())) {
                        SendResponseUtil.sendResponse(
                                new ApiResponse(false, "access denied: insufficient permission on method", null, 403),
                                response);
                        return;
                    }
                }
            } catch (NoSuchMethodException e) {
                LOG.info("No method {} — skipping method-level check", servletMethod);
            }

        } catch (Exception e) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "authentication error", null, 403), response);
            LOG.error("auth exception", e);
            AuthContext.clear();
            return;
        }

        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean checkRole(AuthUser user, RequiresRole annotation) {
        return annotation.matchAll()
                ? user.hasAllRoles(annotation.value())
                : user.hasAnyRoles(annotation.value());
    }

    private String resolveServletMethod(String httpMethod) {
        switch (httpMethod.toUpperCase()) {
            case "GET":    return "doGet";
            case "POST":   return "doPost";
            case "PUT":    return "doPut";
            case "DELETE": return "doDelete";
            case "PATCH":  return "doPatch";
            default:       return "doGet";
        }
    }
}



