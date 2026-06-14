package com.social.app.module.iam.models;

import java.util.List;

/**
 * Returned by all auth endpoints.
 *
 * When needsOrgSelection == true the accessToken is a short-lived pre-auth
 * token (no tenant scope). tenants lists the orgs the user belongs to for
 * an org-picker UI. If tenants is empty the user has no org yet and should
 * POST to /api/tenant/register to create one.
 * Call POST /auth/switch-tenant with chosen tenantId to get a scoped token.
 */
public class TokenResponse {
    private String           accessToken;
    private String           tokenType = "Bearer";
    private String           refreshToken;
    private long             expiresIn;
    private boolean          needsOrgSelection;
    private List<TenantInfo> tenants;

    public String getAccessToken()                         { return accessToken; }
    public void   setAccessToken(String v)                 { this.accessToken = v; }

    public String getTokenType()                           { return tokenType; }
    public void   setTokenType(String v)                   { this.tokenType = v; }

    public String getRefreshToken()                        { return refreshToken; }
    public void   setRefreshToken(String v)                { this.refreshToken = v; }

    public long   getExpiresIn()                           { return expiresIn; }
    public void   setExpiresIn(long v)                     { this.expiresIn = v; }

    public boolean isNeedsOrgSelection()                   { return needsOrgSelection; }
    public void    setNeedsOrgSelection(boolean v)         { this.needsOrgSelection = v; }

    public List<TenantInfo> getTenants()                   { return tenants; }
    public void             setTenants(List<TenantInfo> v) { this.tenants = v; }
}
