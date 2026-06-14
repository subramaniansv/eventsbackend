package com.social.app.module.iam.controllers;

import com.social.app.module.iam.models.*;
import com.social.app.module.iam.security.RequiresRole;
import com.social.app.module.iam.services.MapperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only role/permission mapping.
 *
 *   POST /map?userId=<uid>&roleId=<id>         assign role to user
 *   POST /map?roleId=<id>&permissionId=<id>    assign permission to role
 *   GET  /map?userId=<uid>                     list user's roles
 *   GET  /map?roleId=<id>                      list role's permissions
 */
@RestController
@RequestMapping("/map")
@RequiresRole("Admin")
public class RoleMappingController {

    private final MapperService service = new MapperService();

    @PostMapping
    public ResponseEntity<ApiResponse> map(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String permissionId) {

        if (userId != null) {
            if (roleId == null) return err(400, "roleId is required");
            Long rid = parseLong(roleId);
            if (rid == null) return err(400, "invalid roleId");
            boolean ok = service.mapRoleAndUser(userId, rid);
            return ResponseEntity.ok(new ApiResponse(ok, "user mapped with the role", null, 200));
        }

        if (roleId == null || permissionId == null)
            return err(400, "roleId and permissionId are required");
        Long rid = parseLong(roleId);
        Long pid = parseLong(permissionId);
        if (rid == null || pid == null) return err(400, "invalid roleId or permissionId");
        boolean ok = service.mapRoleAndPermission(rid, pid);
        return ResponseEntity.ok(new ApiResponse(ok, "permission mapped with the role", null, 200));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> get(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId) {

        if (userId != null) {
            List<Role> roles = service.getRolesByUserId(userId);
            return ResponseEntity.ok(new ApiResponse(true, "user mapped roles", roles, 200));
        }
        if (roleId == null) return err(400, "roleId is required");
        Long rid = parseLong(roleId);
        if (rid == null) return err(400, "invalid roleId");
        List<Permission> permissions = service.getPermissionsbyRoleId(rid);
        return ResponseEntity.ok(new ApiResponse(true, "role mapped permissions", permissions, 200));
    }

    private static Long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
