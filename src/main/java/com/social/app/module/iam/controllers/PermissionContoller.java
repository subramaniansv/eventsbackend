package com.social.app.module.iam.controllers;

import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.models.Permission;
import com.social.app.module.iam.security.RequiresRole;
import com.social.app.module.iam.services.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/permission")
@RequiresRole("Admin")
public class PermissionContoller {

    private final PermissionService service = new PermissionService();

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody Permission permission) {
        permission = service.create(permission);
        boolean ok = permission.getId() != null;
        return ResponseEntity.status(ok ? 200 : 400)
                .body(new ApiResponse(ok, ok ? "permission created" : "permission not created", permission, ok ? 200 : 400));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> get(
            @RequestParam(required = false) String permissionId) {
        if (permissionId != null) {
            Long id;
            try { id = Long.parseLong(permissionId); } catch (NumberFormatException e) {
                return err(400, "invalid permissionId");
            }
            return ok("permission fetched", service.getPermissionById(id));
        }
        return ok("all permissions fetched", service.getPermissions());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> delete(
            @RequestParam(required = false) String permissionId) {
        if (permissionId == null) return err(400, "id is required");
        Long id;
        try { id = Long.parseLong(permissionId); } catch (NumberFormatException e) {
            return err(400, "invalid permissionId");
        }
        service.deletePermission(id);
        return ok("permission deleted", null);
    }

    private static ResponseEntity<ApiResponse> ok(String msg, Object data) {
        return ResponseEntity.ok(new ApiResponse(true, msg, data, 200));
    }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
