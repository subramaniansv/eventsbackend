package com.social.app.module.channel.repository;

import com.social.app.config.DBConfig;
import com.social.app.module.channel.models.OAuthState;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for oauth_states — CSRF state token management.
 */
@Repository
public class OAuthStateRepository {

    private static final DataSource DS = DBConfig.getDataSource();

    /**
     * Persists a new state record.
     * @param rawState   the token sent to Meta (NOT stored — only its hash)
     * @param tenantId
     * @param userId
     * @param platform   e.g. "META"
     * @return the OAuthState as persisted
     */
    public OAuthState create(String rawState, UUID tenantId, UUID userId, String platform) {
        String hash = sha256(rawState);
        String sql  = """
            INSERT INTO oauth_states (tenant_id, user_id, state_hash, platform, expires_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING *
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            ps.setString(3, hash);
            ps.setString(4, platform);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now().plusMinutes(10)));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create OAuth state", e);
        }
        throw new RuntimeException("oauth_states insert returned no row");
    }

    /**
     * Finds an unused, non-expired state by the raw token value.
     * Marks it as used atomically to prevent replay.
     */
    public Optional<OAuthState> consumeByRawState(String rawState) {
        String hash = sha256(rawState);
        String sql  = """
            UPDATE oauth_states
               SET used_at = NOW()
             WHERE state_hash = ?
               AND used_at IS NULL
               AND expires_at > NOW()
            RETURNING *
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to consume OAuth state", e);
        }
        return Optional.empty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static OAuthState mapRow(ResultSet rs) throws SQLException {
        OAuthState s = new OAuthState();
        s.setStateId(rs.getObject("state_id", UUID.class));
        s.setTenantId(rs.getObject("tenant_id", UUID.class));
        s.setUserId(rs.getObject("user_id", UUID.class));
        s.setStateHash(rs.getString("state_hash"));
        s.setPlatform(rs.getString("platform"));
        s.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        Timestamp used = rs.getTimestamp("used_at");
        if (used != null) s.setUsedAt(used.toLocalDateTime());
        s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return s;
    }
}
