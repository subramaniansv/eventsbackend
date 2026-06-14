package com.social.app.module.iam.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.social.app.module.iam.models.*;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import com.social.app.module.iam.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /auth               register
 * POST /auth?isLogin=true  login
 * POST /auth?isGoogle=true Google sign-in
 * POST /auth?isRefresh     refresh access token
 * DELETE /auth             logout (revoke one or all refresh tokens)
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service = new AuthService();

    @PostMapping
    public ResponseEntity<ApiResponse> handleAuth(
            HttpServletRequest req,
            @RequestBody(required = false) JsonNode body) {

        boolean isRefresh = req.getParameter("isRefresh") != null;
        boolean isGoogle  = Boolean.parseBoolean(req.getParameter("isGoogle"));
        boolean isLogin   = Boolean.parseBoolean(req.getParameter("isLogin"));

        // ── Refresh ──────────────────────────────────────────────────────────
        if (isRefresh) {
            String token = field(body, "token");
            if (token == null || token.isBlank())
                return err(400, "refresh token required in body");
            try {
                return ok("token refreshed", service.refreshAccessToken(token));
            } catch (Exception e) {
                return err(401, e.getMessage());
            }
        }

        // ── Google sign-in ───────────────────────────────────────────────────
        if (isGoogle) {
            String credential = field(body, "credential");
            if (credential == null || credential.isBlank())
                return err(400, "google credential required");
            RefreshToken rt = buildRefreshToken(req);
            try {
                return ok("user logged in", service.loginWithGoogle(credential, rt));
            } catch (Exception e) {
                String msg = blank(e.getMessage()) ? "google sign-in failed" : e.getMessage();
                return err(401, msg);
            }
        }

        // ── Register / login ─────────────────────────────────────────────────
        User user = parseUser(body);
        if (user == null) return err(400, "invalid payload");

        RefreshToken rt = buildRefreshToken(req);

        if (isLogin) {
            try {
                return ok("user logged in", service.login(user, rt));
            } catch (Exception e) {
                String msg = blank(e.getMessage()) ? "user not logged in" : e.getMessage();
                return err(401, msg);
            }
        } else {
            try {
                return ok("user registered", service.register(user, rt));
            } catch (Exception e) {
                String msg = blank(e.getMessage()) ? "user not registered" : e.getMessage();
                return err(400, msg);
            }
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> logout(
            HttpServletRequest req,
            @RequestBody(required = false) JsonNode body) {

        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null)
            return err(401, "unauthorized");

        boolean revokeAll = "true".equalsIgnoreCase(req.getParameter("all"));
        if (revokeAll) {
            try {
                service.deleteAll(caller.getUserId().toString());
                return ok("all refresh tokens revoked", null);
            } catch (Exception e) {
                return err(400, e.getMessage());
            }
        }

        String token = field(body, "token");
        if (token == null || token.isBlank())
            return err(400, "refresh token required in body");
        try {
            service.deleteByRefreshId(token, caller.getUserId());
            return ok("logged out", null);
        } catch (Exception e) {
            return err(400, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RefreshToken buildRefreshToken(HttpServletRequest req) {
        RefreshToken rt = new RefreshToken();
        rt.setIpAddress(req.getRemoteAddr());
        rt.setUserAgent(req.getHeader("User-Agent"));
        return rt;
    }

    private static User parseUser(JsonNode body) {
        if (body == null) return null;
        try {
            User u = new User();
            if (body.hasNonNull("email"))      u.setEmail(body.get("email").asText());
            if (body.hasNonNull("password"))   u.setPasswordHash(body.get("password").asText());
            if (body.hasNonNull("tenantName")) u.setOrgName(body.get("tenantName").asText());
            if (body.hasNonNull("orgName"))    u.setOrgName(body.get("orgName").asText());
            return u;
        } catch (Exception e) { return null; }
    }

    private static String field(JsonNode body, String key) {
        if (body == null || !body.hasNonNull(key)) return null;
        String v = body.get(key).asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static ResponseEntity<ApiResponse> ok(String msg, Object data) {
        return ResponseEntity.ok(new ApiResponse(true, msg, data, 200));
    }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
