package com.social.app.module.iam.util;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.module.iam.config.ENVConfig;
import com.social.app.module.iam.models.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JwtUtil {

    // Secrets are read from environment — never hardcoded.
    // Required env vars: JWT_ACCESS_SECRET, JWT_REFRESH_SECRET
    private final String accessSecret;
    private final String refreshSecret;

    private static final long ACCESS_EXPIRY        = 86_400_000L;       // 24 h
    private static final long REFRESH_EXPIRY       = 7 * 86_400_000L;   // 7 d
    private static final long PRE_AUTH_EXPIRY      = 5 * 60_000L;       // 5 min (org-picker)

    ObjectMapper mapper = new ObjectMapper();

    public JwtUtil() {
        String as = ENVConfig.get("JWT_ACCESS_SECRET");
        String rs = ENVConfig.get("JWT_REFRESH_SECRET");
        if (as == null || as.isBlank())  throw new IllegalStateException("JWT_ACCESS_SECRET env var is not set");
        if (rs == null || rs.isBlank())  throw new IllegalStateException("JWT_REFRESH_SECRET env var is not set");
        this.accessSecret  = as;
        this.refreshSecret = rs;
    }

    /** Full access token — includes tenantId (may be null for pre-auth). */
    public String generateAccessToken(UUID userId, String email, List<Role> roles, UUID tenantId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email",    email)
                .claim("roles",    roles)
                .claim("tenantId", tenantId != null ? tenantId.toString() : null)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRY))
                .signWith(SignatureAlgorithm.HS256, accessSecret)
                .compact();
    }

    /**
     * Short-lived pre-auth token issued when a Google user has multiple orgs
     * or none at all. tenantId claim is absent (null). Valid for 5 minutes
     * and accepted ONLY on /auth/switch-tenant and /api/tenant/register.
     */
    public String generatePreAuthToken(UUID userId, String email, List<Role> roles) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email",    email)
                .claim("roles",    roles)
                .claim("preAuth",  true)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + PRE_AUTH_EXPIRY))
                .signWith(SignatureAlgorithm.HS256, accessSecret)
                .compact();
    }

    public String generateRefreshToken(UUID userId, UUID tenantId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("tenantId", tenantId != null ? tenantId.toString() : null)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRY))
                .signWith(SignatureAlgorithm.HS256, refreshSecret)
                .compact();
    }

    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .setSigningKey(accessSecret)
                .parseClaimsJws(token)
                .getBody();
    }

    public Claims validateRefreshToken(String token) {
        return Jwts.parser()
                .setSigningKey(refreshSecret)
                .parseClaimsJws(token)
                .getBody();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(validateAccessToken(token).getSubject());
    }

    public String extractEmail(String token) {
        return validateAccessToken(token).get("email", String.class);
    }

    public UUID extractTenantId(String token) {
        String raw = validateAccessToken(token).get("tenantId", String.class);
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw);
    }

    public UUID extractTenantIdFromRefresh(String token) {
        String raw = validateRefreshToken(token).get("tenantId", String.class);
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw);
    }

    public boolean isPreAuth(String token) {
        Boolean flag = validateAccessToken(token).get("preAuth", Boolean.class);
        return Boolean.TRUE.equals(flag);
    }

    public List<Role> extractRoles(String token) {
        Claims claims = validateAccessToken(token);
        return mapper.convertValue(
                claims.get("roles"),
                mapper.getTypeFactory().constructCollectionType(List.class, Role.class));
    }

    public boolean isTokenExpired(String token) {
        try {
            validateAccessToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public String refreshAccessToken(String refreshToken, String email, List<Role> roles, UUID tenantId) {
        Claims claims = validateRefreshToken(refreshToken);
        UUID userId = UUID.fromString(claims.getSubject());
        return generateAccessToken(userId, email, roles, tenantId);
    }
}

