package com.social.app.module.iam.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an organisation / workspace in the multi-tenant IAM.
 * status: TRIAL | ACTIVE | SUSPENDED
 * plan  : FREE  | PRO    | ENTERPRISE
 */
public class Tenant {
    private UUID          tenantId;
    private String        name;
    private String        slug;
    private String        status;
    private String        plan;
    private UUID          ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UUID getTenantId()                       { return tenantId; }
    public void setTenantId(UUID tenantId)          { this.tenantId = tenantId; }

    public String getName()                         { return name; }
    public void setName(String name)                { this.name = name; }

    public String getSlug()                         { return slug; }
    public void setSlug(String slug)                { this.slug = slug; }

    public String getStatus()                       { return status; }
    public void setStatus(String status)            { this.status = status; }

    public String getPlan()                         { return plan; }
    public void setPlan(String plan)                { this.plan = plan; }

    public UUID getOwnerId()                        { return ownerId; }
    public void setOwnerId(UUID ownerId)            { this.ownerId = ownerId; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt()             { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
