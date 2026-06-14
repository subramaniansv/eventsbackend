package com.social.app.module.iam.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.services.PasswordResetService;
import com.social.app.module.iam.services.PasswordResetService.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public forgot-password endpoints (no JWT required).
 *
 *   POST /api/password-reset            body: { "email": "..." }
 *   POST /api/password-reset/confirm    body: { "token": "...", "newPassword": "..." }
 */
@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetController.class);
    private final PasswordResetService service = new PasswordResetService();

    @PostMapping
    public ResponseEntity<ApiResponse> requestReset(
            @RequestBody(required = false) JsonNode body) {
        String email = field(body, "email");
        try {
            service.requestReset(email);
        } catch (Exception e) {
            LOG.warn("password reset request error: {}", e.getMessage());
        }
        // Always 200 so the endpoint can't be used to probe which emails are registered.
        return ResponseEntity.ok(new ApiResponse(true, "if that email exists, a reset link has been sent", null, 200));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse> confirm(
            @RequestBody(required = false) JsonNode body) {
        String token       = field(body, "token");
        String newPassword = field(body, "newPassword");
        if (blank(token) || blank(newPassword))
            return err(400, "token and newPassword are required");
        try {
            Result result = service.reset(token, newPassword);
            return switch (result) {
                case RESET        -> ResponseEntity.ok(new ApiResponse(true, "password reset successfully", null, 200));
                case EXPIRED      -> err(410, "reset link has expired — request a new one");
                case ALREADY_USED -> err(400, "reset link has already been used");
                case WEAK_PASSWORD -> err(400, "password does not meet complexity requirements");
                default           -> err(400, "invalid or unknown reset token");
            };
        } catch (Exception e) {
            LOG.error("password reset confirm error", e);
            return err(500, "could not reset password");
        }
    }

    private static String field(JsonNode body, String key) {
        if (body == null || !body.hasNonNull(key)) return null;
        String v = body.get(key).asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
