package com.social.app.module.channel.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a short-lived CSRF state token generated at OAuth init time.
 * Stored in DB and verified on callback — prevents CSRF and token injection.
 */
public class OAuthState {
    private UUID          stateId;
    private UUID          tenantId;
    private UUID          userId;
    private String        stateHash;   // SHA-256(rawStateToken) — stored; raw sent to Meta
    private String        platform;    // "META"
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;

    public UUID          getStateId()           { return stateId; }
    public void          setStateId(UUID v)     { this.stateId = v; }
    public UUID          getTenantId()          { return tenantId; }
    public void          setTenantId(UUID v)    { this.tenantId = v; }
    public UUID          getUserId()            { return userId; }
    public void          setUserId(UUID v)      { this.userId = v; }
    public String        getStateHash()         { return stateHash; }
    public void          setStateHash(String v) { this.stateHash = v; }
    public String        getPlatform()          { return platform; }
    public void          setPlatform(String v)  { this.platform = v; }
    public LocalDateTime getExpiresAt()         { return expiresAt; }
    public void          setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
    public LocalDateTime getUsedAt()            { return usedAt; }
    public void          setUsedAt(LocalDateTime v)    { this.usedAt = v; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
