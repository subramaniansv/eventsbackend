package com.social.app.config;

import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import com.social.app.module.iam.security.RequiresPermission;
import com.social.app.module.iam.security.RequiresRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * Enforces {@link RequiresRole} and {@link RequiresPermission} annotations
 * on Spring MVC controller classes and methods.
 *
 * This is the Spring equivalent of the servlet-based class/method annotation
 * check that lived in the old AuthorizationFilter.
 *
 * Annotation precedence (same as before):
 *   Class-level  @RequiresRole  →  Class-level  @RequiresPermission
 *   Method-level @RequiresRole  →  Method-level @RequiresPermission
 */
@Component
public class AnnotationSecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Only inspect @Controller / @RestController handler methods.
        if (!(handler instanceof HandlerMethod hm)) return true;

        AuthUser user = AuthContext.get();
        // Unauthenticated (public path allowed by SecurityConfig) — skip checks.
        if (user == null) return true;

        Class<?> controllerClass = hm.getBeanType();
        Method   method          = hm.getMethod();

        // ── Class-level @RequiresRole ────────────────────────────────────────
        RequiresRole classRole = controllerClass.getAnnotation(RequiresRole.class);
        if (classRole != null && !checkRole(user, classRole)) {
            JwtAuthFilter.writeJson(response, 403, "access denied: insufficient role");
            return false;
        }

        // ── Class-level @RequiresPermission ──────────────────────────────────
        RequiresPermission classPerm = controllerClass.getAnnotation(RequiresPermission.class);
        if (classPerm != null && !user.hasPermission(classPerm.resource(), classPerm.action())) {
            JwtAuthFilter.writeJson(response, 403, "access denied: insufficient permission");
            return false;
        }

        // ── Method-level @RequiresRole ───────────────────────────────────────
        RequiresRole methodRole = method.getAnnotation(RequiresRole.class);
        if (methodRole != null && !checkRole(user, methodRole)) {
            JwtAuthFilter.writeJson(response, 403, "access denied: insufficient role");
            return false;
        }

        // ── Method-level @RequiresPermission ─────────────────────────────────
        RequiresPermission methodPerm = method.getAnnotation(RequiresPermission.class);
        if (methodPerm != null && !user.hasPermission(methodPerm.resource(), methodPerm.action())) {
            JwtAuthFilter.writeJson(response, 403, "access denied: insufficient permission");
            return false;
        }

        return true;
    }

    private boolean checkRole(AuthUser user, RequiresRole annotation) {
        return annotation.matchAll()
                ? user.hasAllRoles(annotation.value())
                : user.hasAnyRoles(annotation.value());
    }
}
