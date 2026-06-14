package com.social.app.module.post.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Post {

    private UUID          postId;
    private UUID          tenantId;
    private UUID          channelId;
    private UUID          createdBy;
    private String        content;
    private List<String>  mediaUrls;
    private PostStatus    status;
    private String        platformPostId;   // ID from Meta after publishing
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private String        failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UUID getPostId()                          { return postId; }
    public void setPostId(UUID postId)               { this.postId = postId; }

    public UUID getTenantId()                        { return tenantId; }
    public void setTenantId(UUID tenantId)           { this.tenantId = tenantId; }

    public UUID getChannelId()                       { return channelId; }
    public void setChannelId(UUID channelId)         { this.channelId = channelId; }

    public UUID getCreatedBy()                       { return createdBy; }
    public void setCreatedBy(UUID createdBy)         { this.createdBy = createdBy; }

    public String getContent()                       { return content; }
    public void setContent(String content)           { this.content = content; }

    public List<String> getMediaUrls()               { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }

    public PostStatus getStatus()                    { return status; }
    public void setStatus(PostStatus status)         { this.status = status; }

    public String getPlatformPostId()                { return platformPostId; }
    public void setPlatformPostId(String id)         { this.platformPostId = id; }

    public LocalDateTime getScheduledAt()            { return scheduledAt; }
    public void setScheduledAt(LocalDateTime t)      { this.scheduledAt = t; }

    public LocalDateTime getPublishedAt()            { return publishedAt; }
    public void setPublishedAt(LocalDateTime t)      { this.publishedAt = t; }

    public String getFailureReason()                 { return failureReason; }
    public void setFailureReason(String r)           { this.failureReason = r; }

    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void setCreatedAt(LocalDateTime t)        { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()              { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)        { this.updatedAt = t; }
}
