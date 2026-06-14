package com.social.app.module.iam.services;

import java.util.List;
import java.util.UUID;

import com.social.app.module.iam.models.*;
import com.social.app.module.iam.repository.AuthRepository;
import com.social.app.module.iam.repository.MapperRepository;
import com.social.app.module.iam.repository.TenantRepository;
import com.social.app.module.iam.repository.UserRepository;
import com.social.app.module.iam.util.GoogleTokenVerifier;
import com.social.app.module.iam.util.JwtUtil;
import com.social.app.module.iam.util.PasswordUtil;
import com.social.app.module.mail.MailService;
import com.social.app.module.mail.MailTemplates;

public class AuthService {

    private final AuthRepository    authRepository   = new AuthRepository();
    private final UserRepository    userRepository   = new UserRepository();
    private final MapperRepository  mapperRepository = new MapperRepository();
    private final TenantRepository  tenantRepository = new TenantRepository();
    private final TenantService     tenantService    = new TenantService();
    private final JwtUtil           jwtUtil          = new JwtUtil();

    // -------------------------------------------------------------------------
    // Register (email + password) — creates user + org in one shot
    // -------------------------------------------------------------------------

    public TokenResponse register(User user, RefreshToken refreshToken) {
        user = userRepository.create(user);
        if (user == null || user.getId() == null) {
            throw new RuntimeException("user not registered (email may already exist)");
        }

        // Create the org the user specified in `orgName`
        UUID tenantId = null;
        if (user.getOrgName() != null && !user.getOrgName().isBlank()) {
            Tenant tenant = tenantService.registerTenant(user.getOrgName(), user.getId());
            tenantId = tenant.getTenantId();
        }

        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(user.getId(), tenantId);
        TokenResponse tokenResponse = buildTokenResponse(user, roles, tenantId, refreshToken);

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            MailService.get().send(user.getEmail(), "Welcome", MailTemplates.welcome(user.getFirstName()));
            try { new EmailVerificationService().send(user); } catch (Exception ignore) { }
        }
        return tokenResponse;
    }

    // -------------------------------------------------------------------------
    // Login (email + password)
    // -------------------------------------------------------------------------

    public TokenResponse login(User user, RefreshToken refreshToken) throws RuntimeException {
        User userDB = userRepository.getUserWithPassword(user.getEmail());
        if (userDB == null || userDB.getId() == null) {
            throw new RuntimeException("invalid email or password");
        }
        if (!userDB.getStatus().equals(UserStatus.ACTIVE)) {
            throw new RuntimeException("user status is " + userDB.getStatus().name());
        }
        if (UserRepository.OAUTH_PASSWORD_SENTINEL.equals(userDB.getPasswordHash())) {
            throw new RuntimeException("This account was created with Google. Please continue with Google sign-in.");
        }
        if (!PasswordUtil.verify(user.getPasswordHash(), userDB.getPasswordHash())) {
            throw new RuntimeException("invalid email or password");
        }

        // Determine active tenant: user's first active membership
        List<TenantInfo> tenants = tenantRepository.getTenantsForUser(userDB.getId());
        UUID tenantId = tenants.isEmpty() ? null : tenants.get(0).getTenantId();

        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(userDB.getId(), tenantId);
        TokenResponse tokenResponse = buildTokenResponse(userDB, roles, tenantId, refreshToken);
        userRepository.updateLastLogin(userDB.getId());

        if ("true".equalsIgnoreCase(com.social.app.module.iam.config.ENVConfig.get("MAIL_LOGIN_ALERTS"))
                && userDB.getEmail() != null) {
            MailService.get().send(
                    userDB.getEmail(),
                    "New sign-in to your account",
                    MailTemplates.loginAlert(userDB.getFirstName(),
                            refreshToken.getIpAddress(), refreshToken.getUserAgent()));
        }
        return tokenResponse;
    }

    // -------------------------------------------------------------------------
    // Google sign-in — org-picker aware
    // -------------------------------------------------------------------------

    /**
     * Google-first auth flow:
     *  1. Verify the Google ID token.
     *  2. Look up user by google_sub (preferred) or email (legacy fallback).
     *  3. If new user  → create account.
     *  4. Check tenant memberships:
     *     - 0 tenants → pre-auth token + needsOrgSelection (create org next)
     *     - 1 tenant  → full scoped token, enter app immediately
     *     - N tenants → pre-auth token + org list (user picks one)
     *  5. Client calls POST /auth/switch-tenant with chosen tenantId to get
     *     a fully scoped token.
     */
    public TokenResponse loginWithGoogle(String credential, RefreshToken refreshToken) {
        GoogleTokenVerifier.GoogleProfile profile = GoogleTokenVerifier.verify(credential);

        // Lookup: prefer google_sub for precision, fall back to email
        User user = userRepository.getUserByGoogleSub(profile.sub);
        boolean isNewUser = false;
        if (user == null || user.getId() == null) {
            user = userRepository.getUserWithPassword(profile.email);
            if (user == null || user.getId() == null) {
                // Brand new user
                User toCreate = new User();
                toCreate.setEmail(profile.email);
                toCreate.setFirstName(profile.firstName);
                toCreate.setLastName(profile.lastName);
                toCreate.setGoogleSub(profile.sub);
                user = userRepository.createOAuthUser(toCreate);
                if (user == null || user.getId() == null) {
                    throw new RuntimeException("could not create account");
                }
                isNewUser = true;
                MailService.get().send(user.getEmail(), "Welcome", MailTemplates.welcome(user.getFirstName()));
            } else {
                // Existing password user — link google_sub for future fast lookup
                userRepository.linkGoogleSub(user.getId(), profile.sub);
                if (!user.getStatus().equals(UserStatus.ACTIVE)) {
                    throw new RuntimeException("user status is " + user.getStatus().name());
                }
            }
        } else if (!user.getStatus().equals(UserStatus.ACTIVE)) {
            throw new RuntimeException("user status is " + user.getStatus().name());
        }

        userRepository.updateLastLogin(user.getId());
        List<TenantInfo> tenants = tenantRepository.getTenantsForUser(user.getId());

        // No orgs or multiple orgs → org-picker flow
        if (tenants.isEmpty() || tenants.size() > 1) {
            // Issue a short-lived pre-auth token (no tenant scope)
            List<Role> sysRoles = mapperRepository.getRolesAndPermissionsByUserId(user.getId(), null);
            String preAuthToken = jwtUtil.generatePreAuthToken(user.getId(), user.getEmail(), sysRoles);
            TokenResponse tr = new TokenResponse();
            tr.setAccessToken(preAuthToken);
            tr.setTokenType("Bearer");
            tr.setNeedsOrgSelection(true);
            tr.setTenants(tenants);
            return tr;
        }

        // Exactly one org → auto-enter
        UUID tenantId = tenants.get(0).getTenantId();
        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(user.getId(), tenantId);
        return buildTokenResponse(user, roles, tenantId, refreshToken);
    }

    // -------------------------------------------------------------------------
    // Switch tenant — exchanges pre-auth token for a fully scoped token
    // -------------------------------------------------------------------------

    public TokenResponse switchTenant(UUID userId, UUID tenantId, RefreshToken refreshToken) {
        if (!tenantRepository.isMember(tenantId, userId)) {
            throw new RuntimeException("you are not a member of this organisation");
        }
        Tenant tenant = tenantRepository.findById(tenantId);
        if (tenant == null || "SUSPENDED".equals(tenant.getStatus())) {
            throw new RuntimeException("organisation is not available");
        }
        User user = userRepository.getUser(userId);
        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(userId, tenantId);
        return buildTokenResponse(user, roles, tenantId, refreshToken);
    }

    // -------------------------------------------------------------------------
    // Token refresh
    // -------------------------------------------------------------------------

    public TokenResponse refreshAccessToken(String refreshTokenString) throws RuntimeException {
        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            throw new RuntimeException("invalid refresh token");
        }
        try { jwtUtil.validateRefreshToken(refreshTokenString); }
        catch (Exception e) { throw new RuntimeException("invalid refresh token"); }

        RefreshToken stored = authRepository.getRefreshTokenByTokenHash(refreshTokenString);
        if (stored == null || stored.getUserId() == null) {
            throw new RuntimeException("invalid refresh token");
        }
        if (stored.isRevoked() || stored.getexpiredAt() < System.currentTimeMillis()) {
            throw new RuntimeException("refresh token expired");
        }

        UUID tenantId = jwtUtil.extractTenantIdFromRefresh(refreshTokenString);
        User user     = userRepository.getUser(stored.getUserId());
        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(user.getId(), tenantId);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), roles, tenantId);
        TokenResponse tr = new TokenResponse();
        tr.setAccessToken(accessToken);
        tr.setRefreshToken(refreshTokenString);
        tr.setExpiresIn(System.currentTimeMillis() + 86_400_000L);
        tr.setTokenType("Bearer");
        return tr;
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    public void deleteAll(String uuid) {
        authRepository.revokeByUserId(UUID.fromString(uuid));
    }

    public void deleteByRefreshId(String refreshToken, UUID expectedUserId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("refresh token required");
        }
        RefreshToken stored = authRepository.getRefreshTokenByTokenHash(refreshToken);
        if (stored == null || stored.getUserId() == null) {
            throw new RuntimeException("refresh token not found");
        }
        if (!expectedUserId.equals(stored.getUserId())) {
            throw new RuntimeException("refresh token does not belong to this user");
        }
        authRepository.revokeByTokenHash(refreshToken, stored.getUserId());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private TokenResponse buildTokenResponse(User user, List<Role> roles,
                                              UUID tenantId, RefreshToken refreshToken) {
        String accessToken        = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), roles, tenantId);
        String refreshTokenString = jwtUtil.generateRefreshToken(user.getId(), tenantId);
        refreshToken.setTokenHash(refreshTokenString);
        refreshToken.setUserId(user.getId());
        authRepository.create(refreshToken);

        TokenResponse tr = new TokenResponse();
        tr.setAccessToken(accessToken);
        tr.setRefreshToken(refreshTokenString);
        tr.setExpiresIn(System.currentTimeMillis() + 86_400_000L);
        tr.setTokenType("Bearer");
        return tr;
    }
}
