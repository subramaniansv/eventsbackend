package com.social.app.module.iam.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Links a user to a tenant with a membership status.
 * status: ACTIVE | SUSPENDED
 */
public class TenantMember {
    private UUID          tenantId;
    private UUID          userId;
    private String        status;
    private LocalDateTime joinedAt;

    // Optional hydrated fields (not persisted, populated by joins)
    private String        userEmail;
    private String        firstName;
    private String        lastName;

    public UUID getTenantId()                         { return tenantId; }
    public void setTenantId(UUID tenantId)            { this.tenantId = tenantId; }

    public UUID getUserId()                           { return userId; }
    public void setUserId(UUID userId)                { this.userId = userId; }

    public String getStatus()                         { return status; }
    public void setStatus(String status)              { this.status = status; }

    public LocalDateTime getJoinedAt()                { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt)   { this.joinedAt = joinedAt; }

    public String getUserEmail()                      { return userEmail; }
    public void setUserEmail(String userEmail)        { this.userEmail = userEmail; }

    public String getFirstName()                      { return firstName; }
    public void setFirstName(String firstName)        { this.firstName = firstName; }

    public String getLastName()                       { return lastName; }
    public void setLastName(String lastName)          { this.lastName = lastName; }
}
