package com.social.app.module.iam.services;

import com.social.app.module.iam.models.*;
import com.social.app.module.iam.repository.MapperRepository;
import com.social.app.module.iam.repository.RoleRepository;
import com.social.app.module.iam.repository.TenantRepository;
import com.social.app.module.iam.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TenantService {

    private final TenantRepository  tenantRepo  = new TenantRepository();
    private final MapperRepository  mapperRepo  = new MapperRepository();
    private final RoleRepository    roleRepo    = new RoleRepository();
    private final UserRepository    userRepo    = new UserRepository();

    // -------------------------------------------------------------------------
    // Tenant registration (called after a user creates their account or via
    // the Google org-creation step).
    // -------------------------------------------------------------------------

    /**
     * Creates a new tenant and makes the caller the first ORG_ADMIN.
     * Looks for a role named "ORG_ADMIN" — creates it if it doesn't exist yet.
     */
    public Tenant registerTenant(String orgName, UUID ownerId) {
        if (orgName == null || orgName.isBlank()) {
            throw new RuntimeException("orgName is required");
        }
        String slug = slugify(orgName);

        // Prevent duplicate slugs
        Tenant existing = tenantRepo.findBySlug(slug);
        if (existing != null) {
            throw new RuntimeException("an organisation with that name already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setName(orgName.trim());
        tenant.setSlug(slug);
        tenant.setOwnerId(ownerId);
        tenant = tenantRepo.create(tenant);
        if (tenant.getTenantId() == null) {
            throw new RuntimeException("failed to create organisation");
        }

        // Add the owner as an active member
        tenantRepo.addMember(tenant.getTenantId(), ownerId);

        // Assign ORG_ADMIN role (tenant-scoped)
        Long orgAdminRoleId = ensureOrgAdminRole(tenant.getTenantId());
        mapperRepo.mapRoleAndUser(ownerId, orgAdminRoleId, tenant.getTenantId());

        return tenant;
    }

    // -------------------------------------------------------------------------
    // Member management (org admin operations)
    // -------------------------------------------------------------------------

    public List<TenantMember> getMembers(UUID tenantId) {
        return tenantRepo.getMembers(tenantId);
    }

    public boolean removeMember(UUID tenantId, UUID userId, UUID requestingUserId) {
        if (userId.equals(requestingUserId)) {
            throw new RuntimeException("cannot remove yourself");
        }
        return tenantRepo.removeMember(tenantId, userId);
    }

    // -------------------------------------------------------------------------
    // Invites
    // -------------------------------------------------------------------------

    private static final long INVITE_TTL_HOURS = 72;

    /**
     * Creates and persists an invite token. The raw token is returned so the
     * caller (controller) can build the acceptance URL and send an email.
     */
    public String createInvite(UUID tenantId, String email, Long roleId, UUID invitedBy) {
        String rawToken = TenantRepository.generateToken();
        String tokenHash = TenantRepository.hash(rawToken);

        TenantInvite invite = new TenantInvite();
        invite.setTenantId(tenantId);
        invite.setEmail(email.toLowerCase().trim());
        invite.setRoleId(roleId);
        invite.setInvitedBy(invitedBy);
        invite.setTokenHash(tokenHash);
        invite.setExpiresAt(LocalDateTime.now().plusHours(INVITE_TTL_HOURS));

        tenantRepo.createInvite(invite);
        return rawToken;
    }

    /**
     * Accepts an invite: validates the token, adds the user as a member,
     * assigns the specified role.
     * Returns the tenant so the controller can issue a scoped token.
     */
    public Tenant acceptInvite(String rawToken, UUID userId) {
        String hash = TenantRepository.hash(rawToken);
        TenantInvite invite = tenantRepo.findInviteByHash(hash);
        if (invite == null) {
            throw new RuntimeException("invalid invite link");
        }
        if (invite.getAcceptedAt() != null) {
            throw new RuntimeException("invite already used");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("invite has expired");
        }
        // Atomic mark-used
        if (!tenantRepo.markInviteAccepted(invite.getInviteId())) {
            throw new RuntimeException("invite already used");
        }
        tenantRepo.addMember(invite.getTenantId(), userId);
        if (invite.getRoleId() != null) {
            mapperRepo.mapRoleAndUser(userId, invite.getRoleId(), invite.getTenantId());
        }
        return tenantRepo.findById(invite.getTenantId());
    }

    // -------------------------------------------------------------------------
    // Super-admin tenant management
    // -------------------------------------------------------------------------

    public List<Tenant> getAllTenants() {
        return tenantRepo.findAll();
    }

    public boolean updateStatus(UUID tenantId, String status) {
        return tenantRepo.updateStatus(tenantId, status);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the id of the ORG_ADMIN role for a tenant, creating it if needed. */
    private Long ensureOrgAdminRole(UUID tenantId) {
        // Look for an existing ORG_ADMIN role scoped to this tenant
        List<Role> roles = roleRepo.getRoles();
        for (Role r : roles) {
            if ("ORG_ADMIN".equalsIgnoreCase(r.getName())
                    && tenantId.equals(r.getTenantId())) {
                return r.getId();
            }
        }
        // Create it
        Role role = new Role();
        role.setName("ORG_ADMIN");
        role.setDescription("Organisation administrator");
        role.setTenantId(tenantId);
        role = roleRepo.create(role);
        return role.getId();
    }

    /** Converts an org name to a URL-safe slug. */
    public static String slugify(String name) {
        return name.trim()
                   .toLowerCase()
                   .replaceAll("[^a-z0-9]+", "-")
                   .replaceAll("^-|-$", "");
    }
}
