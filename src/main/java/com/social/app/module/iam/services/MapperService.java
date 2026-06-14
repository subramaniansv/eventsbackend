package com.social.app.module.iam.services;

import java.util.*;

import com.social.app.module.iam.repository.MapperRepository;
import com.social.app.module.iam.models.*;
import com.social.app.module.iam.security.AuthContext;

public class MapperService {
    MapperRepository mapperRepository = new MapperRepository();

    /**
     * Assigns a role to a user within the caller's tenant.
     * tenantId is taken from AuthContext so it cannot be forged by the client.
     * SUPER_ADMIN (tenantId == null in token) may optionally pass an explicit
     * tenantId via the overloaded variant.
     */
    public boolean mapRoleAndUser(String uuid, Long roleID) {
        UUID userID   = UUID.fromString(uuid);
        UUID tenantId = AuthContext.get() != null ? AuthContext.get().getTenantId() : null;
        return mapperRepository.mapRoleAndUser(userID, roleID, tenantId);
    }

    public boolean mapRoleAndUser(String uuid, Long roleID, UUID tenantId) {
        UUID userID = UUID.fromString(uuid);
        return mapperRepository.mapRoleAndUser(userID, roleID, tenantId);
    }

    public boolean mapRoleAndPermission(Long roleId, Long permissionId) {
        return mapperRepository.mapRoleAndPermission(roleId, permissionId);
    }

    public List<Permission> getPermissionsbyRoleId(Long id) {
        return mapperRepository.getPermissionsbyRoleId(id);
    }

    public List<Role> getRolesByUserId(String uuid) {
        UUID userID   = UUID.fromString(uuid);
        UUID tenantId = AuthContext.get() != null ? AuthContext.get().getTenantId() : null;
        return mapperRepository.getRolesAndPermissionsByUserId(userID, tenantId);
    }
}

