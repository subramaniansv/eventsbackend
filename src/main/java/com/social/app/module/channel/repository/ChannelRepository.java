package com.social.app.module.channel.repository;

import com.social.app.config.DBConfig;
import com.social.app.module.channel.models.Channel;
import org.springframework.stereotype.Repository;
import com.social.app.module.channel.models.ChannelPlatform;
import com.social.app.module.channel.models.ChannelStatus;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for the channels table.
 * All queries use PreparedStatements — no string concatenation.
 */
@Repository
public class ChannelRepository {

    private static final DataSource DS = DBConfig.getDataSource();

    // ── Write operations ─────────────────────────────────────────────────────

    /**
     * Inserts a new channel row.
     * If the same (tenant_id, platform, platform_id) already exists the row is
     * updated instead (upsert) — re-connecting an account refreshes its token.
     */
    public Channel save(Channel ch) {
        String sql = """
            INSERT INTO channels
              (tenant_id, connected_by, platform, platform_id, name, picture_url,
               access_token_enc, token_iv, token_tag, token_expires_at, status, meta_page_id)
            VALUES (?, ?, ?::varchar, ?, ?, ?, ?, ?, ?, ?, ?::varchar, ?)
            ON CONFLICT (tenant_id, platform, platform_id) DO UPDATE SET
              name              = EXCLUDED.name,
              picture_url       = EXCLUDED.picture_url,
              access_token_enc  = EXCLUDED.access_token_enc,
              token_iv          = EXCLUDED.token_iv,
              token_tag         = EXCLUDED.token_tag,
              token_expires_at  = EXCLUDED.token_expires_at,
              status            = EXCLUDED.status,
              meta_page_id      = EXCLUDED.meta_page_id,
              updated_at        = NOW()
            RETURNING *
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1,  ch.getTenantId());
            ps.setObject(2,  ch.getConnectedBy());
            ps.setString(3,  ch.getPlatform().name());
            ps.setString(4,  ch.getPlatformId());
            ps.setString(5,  ch.getName());
            ps.setString(6,  ch.getPictureUrl());
            ps.setString(7,  ch.getAccessTokenEnc());
            ps.setString(8,  ch.getTokenIv());
            ps.setString(9,  ch.getTokenTag());
            if (ch.getTokenExpiresAt() != null) {
                ps.setTimestamp(10, Timestamp.valueOf(ch.getTokenExpiresAt()));
            } else {
                ps.setNull(10, Types.TIMESTAMP);
            }
            ps.setString(11, ch.getStatus().name());
            ps.setString(12, ch.getMetaPageId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save channel", e);
        }
        throw new RuntimeException("Channel save returned no row");
    }

    /**
     * Updates only the token fields when a token is refreshed.
     */
    public void updateToken(UUID channelId, String encToken, String iv, String tag) {
        String sql = """
            UPDATE channels
               SET access_token_enc = ?,
                   token_iv         = ?,
                   token_tag        = ?,
                   updated_at       = NOW()
             WHERE channel_id = ?
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, encToken);
            ps.setString(2, iv);
            ps.setString(3, tag);
            ps.setObject(4, channelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update channel token", e);
        }
    }

    /**
     * Marks a channel as DISCONNECTED (soft delete — keeps history).
     */
    public void disconnect(UUID channelId, UUID tenantId) {
        String sql = """
            UPDATE channels
               SET status = 'DISCONNECTED', updated_at = NOW()
             WHERE channel_id = ? AND tenant_id = ?
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, channelId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to disconnect channel", e);
        }
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all ACTIVE channels for a tenant.
     * Tokens are included — caller must not expose them in API responses.
     */
    public List<Channel> findActiveByTenant(UUID tenantId) {
        String sql = """
            SELECT * FROM channels
             WHERE tenant_id = ? AND status = 'ACTIVE'
             ORDER BY platform, name
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Channel> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list channels for tenant " + tenantId, e);
        }
    }

    /**
     * Returns a single channel by ID, scoped to a tenant for security.
     */
    public Optional<Channel> findById(UUID channelId, UUID tenantId) {
        String sql = "SELECT * FROM channels WHERE channel_id = ? AND tenant_id = ?";
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, channelId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find channel " + channelId, e);
        }
        return Optional.empty();
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private static Channel mapRow(ResultSet rs) throws SQLException {
        Channel ch = new Channel();
        ch.setChannelId(rs.getObject("channel_id", UUID.class));
        ch.setTenantId(rs.getObject("tenant_id",   UUID.class));
        ch.setConnectedBy(rs.getObject("connected_by", UUID.class));
        ch.setPlatform(ChannelPlatform.valueOf(rs.getString("platform")));
        ch.setStatus(ChannelStatus.valueOf(rs.getString("status")));
        ch.setPlatformId(rs.getString("platform_id"));
        ch.setName(rs.getString("name"));
        ch.setPictureUrl(rs.getString("picture_url"));
        ch.setAccessTokenEnc(rs.getString("access_token_enc"));
        ch.setTokenIv(rs.getString("token_iv"));
        ch.setTokenTag(rs.getString("token_tag"));
        ch.setMetaPageId(rs.getString("meta_page_id"));
        Timestamp exp = rs.getTimestamp("token_expires_at");
        if (exp != null) ch.setTokenExpiresAt(exp.toLocalDateTime());
        ch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        ch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return ch;
    }
}
