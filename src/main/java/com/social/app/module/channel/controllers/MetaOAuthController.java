package com.social.app.module.channel.controllers;

import com.social.app.common.ENVConfig;
import com.social.app.module.channel.service.MetaOAuthService;
import com.social.app.module.channel.service.MetaOAuthService.CallbackResult;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Meta OAuth 2.0 controller.
 *
 *   GET /api/oauth/meta/init          Protected — caller must be logged in.
 *                                     Returns the Meta authorization URL.
 *
 *   GET /api/oauth/meta/callback      Public  — Meta redirects here after consent.
 *                                     Exchanges code, saves channels, redirects
 *                                     browser to the frontend success page.
 */
@RestController
@RequestMapping("/api/oauth/meta")
public class MetaOAuthController {

    private static final Logger LOG = LoggerFactory.getLogger(MetaOAuthController.class);

    private final MetaOAuthService metaOAuthService;

    public MetaOAuthController(MetaOAuthService metaOAuthService) {
        this.metaOAuthService = metaOAuthService;
    }

    // ── Step 1: generate the authorization URL ────────────────────────────────

    /**
     * Generates the Meta OAuth authorization URL.
     * The frontend redirects the user to this URL.
     *
     * Response: { success: true, message: "...", data: { authUrl: "https://..." } }
     */
    @GetMapping("/init")
    public ResponseEntity<ApiResponse> init() {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getTenantId() == null)
            return ResponseEntity.status(401).body(
                    new ApiResponse(false, "authentication required", null, 401));

        try {
            String authUrl = metaOAuthService.buildAuthorizationUrl(
                    caller.getTenantId(), caller.getUserId());
            return ResponseEntity.ok(
                    new ApiResponse(true, "oauth url generated",
                            Map.of("authUrl", authUrl), 200));
        } catch (Exception e) {
            LOG.error("Meta OAuth init failed", e);
            return ResponseEntity.status(500).body(
                    new ApiResponse(false, "could not generate authorization url", null, 500));
        }
    }

    // ── Step 2: receive the callback from Meta ────────────────────────────────

    /**
     * Meta redirects here after the user grants (or denies) access.
     *
     * Success flow:
     *   - Validates state (CSRF), exchanges code, saves channels.
     *   - Redirects browser to APP_HOME_URL/channels?connected=N
     *
     * Denial flow (user clicked "Cancel" on Meta's dialog):
     *   - Meta sends ?error=access_denied
     *   - Redirects browser to APP_HOME_URL/channels?error=access_denied
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_reason",      required = false) String errorReason,
            @RequestParam(name = "error_description", required = false) String errorDescription) {

        String frontendBase = ENVConfig.get("APP_HOME_URL", "http://localhost:3000");

        // User cancelled / denied permission on Meta's dialog.
        if (error != null) {
            LOG.warn("Meta OAuth denied: error={} reason={}", error, errorReason);
            String redirectTo = frontendBase + "/channels?error="
                    + URLEncoder.encode(error, StandardCharsets.UTF_8);
            return ResponseEntity.status(302).location(URI.create(redirectTo)).build();
        }

        // Missing required params.
        if (code == null || state == null) {
            return ResponseEntity.status(302)
                    .location(URI.create(frontendBase + "/channels?error=missing_params"))
                    .build();
        }

        try {
            CallbackResult result = metaOAuthService.handleCallback(code, state);
            LOG.info("Meta OAuth callback OK: tenant={} channels={}", result.tenantId(), result.channelsSaved());
            String redirectTo = frontendBase + "/channels?connected=" + result.channelsSaved();
            return ResponseEntity.status(302).location(URI.create(redirectTo)).build();
        } catch (IllegalArgumentException e) {
            // Invalid / expired / replayed state.
            LOG.warn("Meta OAuth callback invalid state: {}", e.getMessage());
            return ResponseEntity.status(302)
                    .location(URI.create(frontendBase + "/channels?error=invalid_state"))
                    .build();
        } catch (Exception e) {
            LOG.error("Meta OAuth callback failed", e);
            return ResponseEntity.status(302)
                    .location(URI.create(frontendBase + "/channels?error=server_error"))
                    .build();
        }
    }
}
