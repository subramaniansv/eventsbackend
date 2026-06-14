package com.social.app.module.iam.models;

import java.util.UUID;

/**
 * Lightweight DTO returned in the org-picker response when a Google user
 * belongs to more than one tenant. The client shows a list of these so the
 * user can choose which org to enter.
 */
public class TenantInfo {
    private UUID   tenantId;
    private String name;
    private String slug;
    private String plan;

    public TenantInfo() {}

    public TenantInfo(UUID tenantId, String name, String slug, String plan) {
        this.tenantId = tenantId;
        this.name     = name;
        this.slug     = slug;
        this.plan     = plan;
    }

    public UUID getTenantId()          { return tenantId; }
    public void setTenantId(UUID v)    { this.tenantId = v; }

    public String getName()            { return name; }
    public void setName(String v)      { this.name = v; }

    public String getSlug()            { return slug; }
    public void setSlug(String v)      { this.slug = v; }

    public String getPlan()            { return plan; }
    public void setPlan(String v)      { this.plan = v; }
}
