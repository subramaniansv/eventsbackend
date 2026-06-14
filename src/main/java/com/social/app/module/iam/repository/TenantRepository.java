package com.social.app.module.iam.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.social.app.module.iam.config.DBConfig;
import com.social.app.module.iam.models.Tenant;
import com.social.app.module.iam.models.TenantInfo;
import com.social.app.module.iam.models.TenantInvite;
import com.social.app.module.iam.models.TenantMember;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class TenantRepository {
    private static final Logger LOG = LoggerFactory.getLogger(TenantRepository.class);

    // -------------------------------------------------------------------------
    // Tenant CRUD
    // -------------------------------------------------------------------------

    public Tenant create(Tenant tenant) {
        String sql = """
                INSERT INTO tenants (name, slug, status, plan, owner_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING tenant_id, created_at, updated_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenant.getName());
            ps.setString(2, tenant.getSlug());
            ps.setString(3, tenant.getStatus() != null ? tenant.getStatus() : "TRIAL");
            ps.setString(4, tenant.getPlan()   != null ? tenant.getPlan()   : "FREE");
            ps.setObject(5, tenant.getOwnerId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                tenant.setTenantId(rs.getObject("tenant_id", UUID.class));
                tenant.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                tenant.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }
        } catch (SQLException e) { LOG.error("create tenant", e); }
        return tenant;
    }

    public Tenant findById(UUID tenantId) {
        String sql = "SELECT * FROM tenants WHERE tenant_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapTenant(rs);
        } catch (SQLException e) { LOG.error("findById tenant", e); }
        return null;
    }

    public Tenant findBySlug(String slug) {
        String sql = "SELECT * FROM tenants WHERE slug = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, slug);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapTenant(rs);
        } catch (SQLException e) { LOG.error("findBySlug tenant", e); }
        return null;
    }

    public List<Tenant> findAll() {
        String sql = "SELECT * FROM tenants ORDER BY created_at DESC";
        List<Tenant> list = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapTenant(rs));
        } catch (SQLException e) { LOG.error("findAll tenants", e); }
        return list;
    }

    public boolean updateStatus(UUID tenantId, String status) {
        String sql = "UPDATE tenants SET status = ?, updated_at = NOW() WHERE tenant_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, tenantId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { LOG.error("updateStatus tenant", e); }
        return false;
    }

    // -------------------------------------------------------------------------
    // Membership
    // -------------------------------------------------------------------------

    public boolean addMember(UUID tenantId, UUID userId) {
        String sql = """
                INSERT INTO tenant_members (tenant_id, user_id, status)
                VALUES (?, ?, 'ACTIVE')
                ON CONFLICT (tenant_id, user_id) DO UPDATE SET status = 'ACTIVE'
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { LOG.error("addMember", e); }
        return false;
    }

    public boolean isMember(UUID tenantId, UUID userId) {
        String sql = "SELECT 1 FROM tenant_members WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE'";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            return ps.executeQuery().next();
        } catch (SQLException e) { LOG.error("isMember", e); }
        return false;
    }

    public boolean removeMember(UUID tenantId, UUID userId) {
        String sql = "DELETE FROM tenant_members WHERE tenant_id = ? AND user_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { LOG.error("removeMember", e); }
        return false;
    }

    public List<TenantMember> getMembers(UUID tenantId) {
        String sql = """
                SELECT tm.user_id, tm.status, tm.joined_at,
                       u.email, u.first_name, u.last_name
                FROM tenant_members tm
                JOIN users u ON u.user_id = tm.user_id
                WHERE tm.tenant_id = ?
                ORDER BY tm.joined_at
                """;
        List<TenantMember> list = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TenantMember m = new TenantMember();
                m.setTenantId(tenantId);
                m.setUserId(rs.getObject("user_id", UUID.class));
                m.setStatus(rs.getString("status"));
                m.setJoinedAt(rs.getTimestamp("joined_at").toLocalDateTime());
                m.setUserEmail(rs.getString("email"));
                m.setFirstName(rs.getString("first_name"));
                m.setLastName(rs.getString("last_name"));
                list.add(m);
            }
        } catch (SQLException e) { LOG.error("getMembers", e); }
        return list;
    }

    /**
     * Returns a lightweight list of tenants the user is an active member of.
     * Used for the org-picker after Google sign-in.
     */
    public List<TenantInfo> getTenantsForUser(UUID userId) {
        String sql = """
                SELECT t.tenant_id, t.name, t.slug, t.plan
                FROM tenants t
                JOIN tenant_members tm ON tm.tenant_id = t.tenant_id
                WHERE tm.user_id = ? AND tm.status = 'ACTIVE' AND t.status != 'SUSPENDED'
                ORDER BY tm.joined_at
                """;
        List<TenantInfo> list = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new TenantInfo(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("plan")));
            }
        } catch (SQLException e) { LOG.error("getTenantsForUser", e); }
        return list;
    }

    // -------------------------------------------------------------------------
    // Invites
    // -------------------------------------------------------------------------

    public TenantInvite createInvite(TenantInvite invite) {
        String sql = """
                INSERT INTO tenant_invites (tenant_id, email, role_id, invited_by, token_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING invite_id, created_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, invite.getTenantId());
            ps.setString(2, invite.getEmail().toLowerCase().trim());
            if (invite.getRoleId() != null) ps.setLong(3, invite.getRoleId()); else ps.setNull(3, Types.BIGINT);
            ps.setObject(4, invite.getInvitedBy());
            ps.setString(5, invite.getTokenHash());
            ps.setTimestamp(6, Timestamp.valueOf(invite.getExpiresAt()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                invite.setInviteId(rs.getObject("invite_id", UUID.class));
                invite.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
        } catch (SQLException e) { LOG.error("createInvite", e); }
        return invite;
    }

    public TenantInvite findInviteByHash(String tokenHash) {
        String sql = "SELECT * FROM tenant_invites WHERE token_hash = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapInvite(rs);
        } catch (SQLException e) { LOG.error("findInviteByHash", e); }
        return null;
    }

    public boolean markInviteAccepted(UUID inviteId) {
        String sql = "UPDATE tenant_invites SET accepted_at = NOW() WHERE invite_id = ? AND accepted_at IS NULL";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, inviteId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { LOG.error("markInviteAccepted", e); }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException("hash error", e); }
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Tenant mapTenant(ResultSet rs) throws SQLException {
        Tenant t = new Tenant();
        t.setTenantId(rs.getObject("tenant_id", UUID.class));
        t.setName(rs.getString("name"));
        t.setSlug(rs.getString("slug"));
        t.setStatus(rs.getString("status"));
        t.setPlan(rs.getString("plan"));
        t.setOwnerId(rs.getObject("owner_id", UUID.class));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        t.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return t;
    }

    private TenantInvite mapInvite(ResultSet rs) throws SQLException {
        TenantInvite i = new TenantInvite();
        i.setInviteId(rs.getObject("invite_id", UUID.class));
        i.setTenantId(rs.getObject("tenant_id", UUID.class));
        i.setEmail(rs.getString("email"));
        Object rid = rs.getObject("role_id");
        if (rid != null) i.setRoleId(((Number) rid).longValue());
        i.setInvitedBy(rs.getObject("invited_by", UUID.class));
        i.setTokenHash(rs.getString("token_hash"));
        i.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        Timestamp acc = rs.getTimestamp("accepted_at");
        if (acc != null) i.setAcceptedAt(acc.toLocalDateTime());
        i.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return i;
    }
}
