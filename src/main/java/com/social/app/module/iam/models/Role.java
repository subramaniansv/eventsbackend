package com.social.app.module.iam.models;

import java.util.List;
import java.util.UUID;

public class Role {
    private Long   id;
    private String name;
    private String description;
    private List<Permission> permissions;
    /** NULL = system-level role (e.g. SUPER_ADMIN); non-null = tenant-scoped. */
    private UUID   tenantId;

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public String getName()                          { return name; }
    public void setName(String name)                 { this.name = name; }

    public String getDescription()                   { return description; }
    public void setDescription(String description)   { this.description = description; }

    public List<Permission> getPermissions()                   { return permissions; }
    public void setPermissions(List<Permission> permissions)   { this.permissions = permissions; }

    public UUID getTenantId()                        { return tenantId; }
    public void setTenantId(UUID tenantId)           { this.tenantId = tenantId; }
}

