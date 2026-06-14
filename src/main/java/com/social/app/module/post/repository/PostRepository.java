package com.social.app.module.post.repository;

import com.social.app.config.DBConfig;
import com.social.app.module.post.models.Post;
import com.social.app.module.post.models.PostStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class PostRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostRepository.class);
    private static final DataSource DS = DBConfig.getDataSource();

    // ── Write ─────────────────────────────────────────────────────────────────

    public Post save(Post post) {
        String sql = """
            INSERT INTO posts
              (tenant_id, channel_id, created_by, content, media_urls, status, scheduled_at)
            VALUES (?, ?, ?, ?, ?, ?::varchar, ?)
            RETURNING *
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, post.getTenantId());
            ps.setObject(2, post.getChannelId());
            ps.setObject(3, post.getCreatedBy());
            ps.setString(4, post.getContent());
            ps.setArray(5,  toSqlArray(conn, post.getMediaUrls()));
            ps.setString(6, post.getStatus() != null ? post.getStatus().name() : PostStatus.DRAFT.name());
            ps.setObject(7, post.getScheduledAt());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            LOG.error("Failed to save post", e);
            throw new RuntimeException("Failed to save post: " + e.getMessage(), e);
        }
        return null;
    }

    public void markPublishing(UUID postId) {
        update("UPDATE posts SET status='PUBLISHING', updated_at=NOW() WHERE post_id=?", postId);
    }

    public void markPublished(UUID postId, String platformPostId) {
        String sql = """
            UPDATE posts
               SET status='PUBLISHED', platform_post_id=?, published_at=NOW(), updated_at=NOW()
             WHERE post_id=?
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, platformPostId);
            ps.setObject(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to mark post published", e);
        }
    }

    public void markFailed(UUID postId, String reason) {
        String sql = """
            UPDATE posts
               SET status='FAILED', failure_reason=?, updated_at=NOW()
             WHERE post_id=?
            """;
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setObject(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to mark post failed", e);
        }
    }

    public void delete(UUID postId, UUID tenantId) {
        String sql = "DELETE FROM posts WHERE post_id=? AND tenant_id=? AND status IN ('DRAFT','FAILED')";
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, postId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to delete post", e);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<Post> findByTenant(UUID tenantId) {
        String sql = "SELECT * FROM posts WHERE tenant_id=? ORDER BY created_at DESC";
        return query(sql, tenantId);
    }

    public Optional<Post> findById(UUID postId, UUID tenantId) {
        String sql = "SELECT * FROM posts WHERE post_id=? AND tenant_id=?";
        List<Post> rows = query(sql, postId, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Returns all SCHEDULED posts whose scheduled_at is in the past and not yet picked up. */
    public List<Post> findDueScheduled() {
        String sql = """
            SELECT * FROM posts
             WHERE status='SCHEDULED'
               AND scheduled_at <= NOW()
            ORDER BY scheduled_at
            """;
        return query(sql);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void update(String sql, Object... args) {
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("SQL update failed: {}", sql, e);
        }
    }

    private List<Post> query(String sql, Object... args) {
        List<Post> list = new ArrayList<>();
        try (Connection conn = DS.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            LOG.error("SQL query failed: {}", sql, e);
        }
        return list;
    }

    private Post map(ResultSet rs) throws SQLException {
        Post p = new Post();
        p.setPostId(rs.getObject("post_id", UUID.class));
        p.setTenantId(rs.getObject("tenant_id", UUID.class));
        p.setChannelId(rs.getObject("channel_id", UUID.class));
        p.setCreatedBy(rs.getObject("created_by", UUID.class));
        p.setContent(rs.getString("content"));
        Array arr = rs.getArray("media_urls");
        if (arr != null) p.setMediaUrls(Arrays.asList((String[]) arr.getArray()));
        p.setStatus(PostStatus.valueOf(rs.getString("status")));
        p.setPlatformPostId(rs.getString("platform_post_id"));
        Timestamp sa = rs.getTimestamp("scheduled_at");
        if (sa != null) p.setScheduledAt(sa.toLocalDateTime());
        Timestamp pa = rs.getTimestamp("published_at");
        if (pa != null) p.setPublishedAt(pa.toLocalDateTime());
        p.setFailureReason(rs.getString("failure_reason"));
        p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return p;
    }

    private Array toSqlArray(Connection conn, List<String> list) throws SQLException {
        if (list == null || list.isEmpty()) return conn.createArrayOf("text", new String[0]);
        return conn.createArrayOf("text", list.toArray(new String[0]));
    }
}
