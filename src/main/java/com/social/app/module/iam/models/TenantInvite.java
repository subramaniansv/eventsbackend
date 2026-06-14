package com.social.app.module.iam.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Email invitation sent by an org admin to bring a user into their tenant.
 */
public class TenantInvite {
    private UUID          inviteId;
    private UUID          tenantId;
    private String        email;
    private Long          roleId;
    private UUID          invitedBy;
    private String        tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;

    public UUID getInviteId()                           { return inviteId; }
    public void setInviteId(UUID inviteId)              { this.inviteId = inviteId; }

    public UUID getTenantId()                           { return tenantId; }
    public void setTenantId(UUID tenantId)              { this.tenantId = tenantId; }

    public String getEmail()                            { return email; }
    public void setEmail(String email)                  { this.email = email; }

    public Long getRoleId()                             { return roleId; }
    public void setRoleId(Long roleId)                  { this.roleId = roleId; }

    public UUID getInvitedBy()                          { return invitedBy; }
    public void setInvitedBy(UUID invitedBy)            { this.invitedBy = invitedBy; }

    public String getTokenHash()                        { return tokenHash; }
    public void setTokenHash(String tokenHash)          { this.tokenHash = tokenHash; }

    public LocalDateTime getExpiresAt()                 { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt)   { this.expiresAt = expiresAt; }

    public LocalDateTime getAcceptedAt()                { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)   { this.createdAt = createdAt; }
}
