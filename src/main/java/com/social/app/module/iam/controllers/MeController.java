package com.social.app.module.iam.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.models.User;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import com.social.app.module.iam.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service endpoints for the authenticated caller.
 *
 *   GET /api/me  — return caller's own profile
 *   PUT /api/me  — change caller's own password
 *                  body: { "oldPassword": "...", "newPassword": "..." }
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final Logger LOG = LoggerFactory.getLogger(MeController.class);
    private final UserService service = new UserService();

    @GetMapping
    public ResponseEntity<ApiResponse> getProfile() {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null)
            return err(401, "unauthenticated");
        try {
            User user = service.getOwnProfile(caller.getUserId());
            if (user == null || user.getId() == null)
                return err(404, "user not found");
            return ok("profile fetched", user);
        } catch (Exception e) {
            LOG.error("MeController getProfile", e);
            return err(500, "could not fetch profile");
        }
    }

    @PutMapping
    public ResponseEntity<ApiResponse> changePassword(
            @RequestBody(required = false) JsonNode body) {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null)
            return err(401, "unauthenticated");

        String oldPassword = field(body, "oldPassword");
        String newPassword = field(body, "newPassword");
        if (blank(oldPassword) || blank(newPassword))
            return err(400, "oldPassword and newPassword are required");
        try {
            service.updatePassword(caller.getUserId(), oldPassword, newPassword);
            return ok("password changed", null);
        } catch (Exception e) {
            String msg = blank(e.getMessage()) ? "could not change password" : e.getMessage();
            return err(400, msg);
        }
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
