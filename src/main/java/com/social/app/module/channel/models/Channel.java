package com.social.app.module.channel.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents one connected social media account (a Facebook Page or an
 * Instagram Business account) belonging to a tenant.
 *
 * Sensitive fields (access_token_enc, token_iv, token_tag) are marked
 * write-only / ignored so they are never serialised into API responses.
 */
public class Channel {

    private UUID   channelId;
    private UUID   tenantId;
    private UUID   connectedBy;

    private ChannelPlatform platform;
    private ChannelStatus   status;

    /** Meta's ID for this Page or Instagram account. */
    private String platformId;

    /** Human-readable name, e.g. "Acme Coffee — Official Page". */
    private String name;

    /** Profile picture URL — safe to return in responses. */
    private String pictureUrl;

    /** For INSTAGRAM rows: the parent Facebook page_id. */
    private String metaPageId;

    // ── Encrypted token fields — never leave the server ──────────────────────

    @JsonIgnore
    private String accessTokenEnc;

    @JsonIgnore
    private String tokenIv;

    @JsonIgnore
    private String tokenTag;

    @JsonProperty(access = Access.READ_ONLY)
    private LocalDateTime tokenExpiresAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public UUID getChannelId()            { return channelId; }
    public void setChannelId(UUID v)      { this.channelId = v; }

    public UUID getTenantId()             { return tenantId; }
    public void setTenantId(UUID v)       { this.tenantId = v; }

    public UUID getConnectedBy()          { return connectedBy; }
    public void setConnectedBy(UUID v)    { this.connectedBy = v; }

    public ChannelPlatform getPlatform()          { return platform; }
    public void setPlatform(ChannelPlatform v)    { this.platform = v; }

    public ChannelStatus getStatus()              { return status; }
    public void setStatus(ChannelStatus v)        { this.status = v; }

    public String getPlatformId()         { return platformId; }
    public void setPlatformId(String v)   { this.platformId = v; }

    public String getName()               { return name; }
    public void setName(String v)         { this.name = v; }

    public String getPictureUrl()         { return pictureUrl; }
    public void setPictureUrl(String v)   { this.pictureUrl = v; }

    public String getMetaPageId()         { return metaPageId; }
    public void setMetaPageId(String v)   { this.metaPageId = v; }

    public String getAccessTokenEnc()     { return accessTokenEnc; }
    public void setAccessTokenEnc(String v){ this.accessTokenEnc = v; }

    public String getTokenIv()            { return tokenIv; }
    public void setTokenIv(String v)      { this.tokenIv = v; }

    public String getTokenTag()           { return tokenTag; }
    public void setTokenTag(String v)     { this.tokenTag = v; }

    public LocalDateTime getTokenExpiresAt()         { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime v)   { this.tokenExpiresAt = v; }

    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void setCreatedAt(LocalDateTime v)        { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()              { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)        { this.updatedAt = v; }
}
