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
import com.social.app.module.iam.security.RequiresRole;
import com.social.app.module.iam.services.AuthService;
import com.social.app.module.iam.services.TenantService;
import com.social.app.module.iam.util.SendResponseUtil;
import com.social.app.module.mail.MailService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Tenant management endpoints.
 *
 * POST /api/tenant/register      — authenticated user creates their first org
 * GET  /api/tenant               — SUPER_ADMIN: list all tenants
 * GET  /api/tenant/members       — ORG_ADMIN: list members in caller's org
 * POST /api/tenant/invite        — ORG_ADMIN: invite a user by email
 * GET  /api/tenant/invite/accept — Public: accept an invite link
 * DELETE /api/tenant/members     — ORG_ADMIN: remove a member
 * PUT  /api/tenant/status        — SUPER_ADMIN: activate / suspend a tenant
 */
@WebServlet("/api/tenant/*")
public class TenantController extends HttpServlet {

    private final TenantService tenantService = new TenantService();
    private final AuthService   authService   = new AuthService();
    private static final ObjectMapper MAPPER  = new ObjectMapper();

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        AuthUser caller = AuthContext.get();
        String pathInfo = req.getPathInfo(); // e.g. "/register", "/invite"

        // --- Create org (pre-auth or freshly registered user) ---
        if ("/register".equals(pathInfo)) {
            JsonNode body = MAPPER.readTree(req.getInputStream());
            String orgName = body.path("orgName").asText(null);
            if (orgName == null || orgName.isBlank()) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "orgName is required", null, 400), res);
                return;
            }
            try {
                Tenant tenant = tenantService.registerTenant(orgName, caller.getUserId());
                // Issue a fully scoped token for the new org
                RefreshToken rt = new RefreshToken();
                rt.setIpAddress(req.getRemoteAddr());
                rt.setUserAgent(req.getHeader("User-Agent"));
                TokenResponse token = authService.switchTenant(caller.getUserId(), tenant.getTenantId(), rt);
                SendResponseUtil.sendResponse(new ApiResponse(true, "organisation created", token, 201), res);
            } catch (Exception e) {
                SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), res);
            }
            return;
        }

        // --- Invite a user ---
        if ("/invite".equals(pathInfo)) {
            requireOrgAdminOrSuperAdmin(caller, res);
            JsonNode body = MAPPER.readTree(req.getInputStream());
            String email  = body.path("email").asText(null);
            Long   roleId = body.hasNonNull("roleId") ? body.path("roleId").asLong() : null;
            if (email == null || email.isBlank()) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "email is required", null, 400), res);
                return;
            }
            try {
                UUID tenantId  = caller.getTenantId();
                String rawToken = tenantService.createInvite(tenantId, email, roleId, caller.getUserId());
                String inviteUrl = buildInviteUrl(req, rawToken);
                // Send invite email (best-effort)
                try {
                    MailService.get().send(email, "You've been invited", buildInviteEmailBody(inviteUrl));
                } catch (Exception ignore) { }
                SendResponseUtil.sendResponse(new ApiResponse(true, "invite sent", null, 200), res);
            } catch (Exception e) {
                SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), res);
            }
            return;
        }

        SendResponseUtil.sendResponse(new ApiResponse(false, "unknown action", null, 404), res);
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        AuthUser caller = AuthContext.get();
        String pathInfo = req.getPathInfo();

        // Accept invite link (public — no AuthContext here, caller may be null)
        if ("/invite/accept".equals(pathInfo)) {
            String token = req.getParameter("token");
            if (token == null || token.isBlank()) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "token is required", null, 400), res);
                return;
            }
            // The user must already be authenticated (pre-auth or full token).
            // The filter allows this path for unauthenticated users so they can
            // log in / register first — check here.
            if (caller == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "sign in first, then visit this link again", null, 401), res);
                return;
            }
            try {
                Tenant tenant = tenantService.acceptInvite(token, caller.getUserId());
                RefreshToken rt = new RefreshToken();
                rt.setIpAddress(req.getRemoteAddr());
                rt.setUserAgent(req.getHeader("User-Agent"));
                TokenResponse tr = authService.switchTenant(caller.getUserId(), tenant.getTenantId(), rt);
                SendResponseUtil.sendResponse(new ApiResponse(true, "invite accepted", tr, 200), res);
            } catch (Exception e) {
                SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), res);
            }
            return;
        }

        // List members in caller's org
        if ("/members".equals(pathInfo)) {
            requireOrgAdminOrSuperAdmin(caller, res);
            UUID tenantId = caller.getTenantId();
            if (tenantId == null && req.getParameter("tenantId") != null) {
                tenantId = UUID.fromString(req.getParameter("tenantId")); // SUPER_ADMIN
            }
            List<TenantMember> members = tenantService.getMembers(tenantId);
            SendResponseUtil.sendResponse(new ApiResponse(true, "members", members, 200), res);
            return;
        }

        // SUPER_ADMIN: list all tenants
        if (caller != null && caller.isSuperAdmin()) {
            List<Tenant> tenants = tenantService.getAllTenants();
            SendResponseUtil.sendResponse(new ApiResponse(true, "tenants", tenants, 200), res);
            return;
        }

        SendResponseUtil.sendResponse(new ApiResponse(false, "forbidden", null, 403), res);
    }

    // -------------------------------------------------------------------------
    // DELETE — remove member
    // -------------------------------------------------------------------------
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        AuthUser caller = AuthContext.get();
        requireOrgAdminOrSuperAdmin(caller, res);
        String userIdRaw = req.getParameter("userId");
        if (userIdRaw == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "userId is required", null, 400), res);
            return;
        }
        try {
            UUID targetUserId = UUID.fromString(userIdRaw);
            UUID tenantId     = caller.getTenantId();
            boolean ok = tenantService.removeMember(tenantId, targetUserId, caller.getUserId());
            SendResponseUtil.sendResponse(new ApiResponse(ok, ok ? "member removed" : "member not found", null, 200), res);
        } catch (Exception e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), res);
        }
    }

    // -------------------------------------------------------------------------
    // PUT — update tenant status (SUPER_ADMIN only)
    // -------------------------------------------------------------------------
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || !caller.isSuperAdmin()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "forbidden", null, 403), res);
            return;
        }
        String tenantIdRaw = req.getParameter("tenantId");
        String status      = req.getParameter("status");
        if (tenantIdRaw == null || status == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "tenantId and status are required", null, 400), res);
            return;
        }
        boolean ok = tenantService.updateStatus(UUID.fromString(tenantIdRaw), status);
        SendResponseUtil.sendResponse(new ApiResponse(ok, "tenant status updated", null, 200), res);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireOrgAdminOrSuperAdmin(AuthUser caller, HttpServletResponse res) throws IOException {
        if (caller == null || (!caller.isSuperAdmin() && !caller.hasRole("ORG_ADMIN"))) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "forbidden", null, 403), res);
            throw new RuntimeException("forbidden");
        }
    }

    private String buildInviteUrl(HttpServletRequest req, String rawToken) {
        String base = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() != 80 && req.getServerPort() != 443
                        ? ":" + req.getServerPort() : "");
        return base + "/api/tenant/invite/accept?token=" + rawToken;
    }

    private String buildInviteEmailBody(String inviteUrl) {
        return "<p>You've been invited to join an organisation.</p>"
             + "<p><a href='" + inviteUrl + "'>Accept invitation</a></p>"
             + "<p>This link expires in 72 hours.</p>";
    }
}
