package com.social.app.module.iam.controllers;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.module.iam.models.*;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import com.social.app.module.iam.services.AuthService;
import com.social.app.module.iam.util.SendResponseUtil;

import java.io.IOException;
import java.util.UUID;

/**
 * POST /auth/switch-tenant
 *
 * Exchanges a pre-auth token (or any valid access token) for a fully scoped
 * token tied to the chosen tenant.
 *
 * Request body: { "tenantId": "<uuid>" }
 *
 * The filter allows any authenticated request here, including pre-auth tokens
 * (those with tenantId == null in the JWT).
 */
@WebServlet("/auth/switch-tenant")
public class SwitchTenantController extends HttpServlet {

    private final AuthService    authService = new AuthService();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), res);
            return;
        }

        JsonNode body = MAPPER.readTree(req.getInputStream());
        String tenantIdRaw = body.path("tenantId").asText(null);
        if (tenantIdRaw == null || tenantIdRaw.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "tenantId is required", null, 400), res);
            return;
        }

        UUID tenantId = UUID.fromString(tenantIdRaw);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setIpAddress(req.getRemoteAddr());
        refreshToken.setUserAgent(req.getHeader("User-Agent"));

        try {
            TokenResponse tokenResponse = authService.switchTenant(caller.getUserId(), tenantId, refreshToken);
            SendResponseUtil.sendResponse(new ApiResponse(true, "tenant switched", tokenResponse, 200), res);
        } catch (Exception e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 403), res);
        }
    }
}
