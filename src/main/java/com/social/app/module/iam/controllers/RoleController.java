package com.social.app.module.iam.controllers;

import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.models.Role;
import com.social.app.module.iam.security.RequiresRole;
import com.social.app.module.iam.services.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/role")
@RequiresRole("Admin")
public class RoleController {

    private final RoleService service = new RoleService();

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody Role role) {
        role = service.create(role);
        boolean ok = role.getId() != null;
        return ResponseEntity.status(ok ? 200 : 400)
                .body(new ApiResponse(ok, ok ? "role created" : "role not created", role, ok ? 200 : 400));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> get(
            @RequestParam(required = false) String roleId) {
        if (roleId != null) {
            Long id;
            try { id = Long.parseLong(roleId); } catch (NumberFormatException e) {
                return err(400, "invalid roleId");
            }
            return ok("role fetched", service.getRoleById(id));
        }
        return ok("all roles fetched", service.getAllRoles());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> delete(
            @RequestParam(required = false) String roleId) {
        if (roleId == null) return err(400, "id is required");
        Long id;
        try { id = Long.parseLong(roleId); } catch (NumberFormatException e) {
            return err(400, "invalid roleId");
        }
        service.deleteRoleById(id);
        return ok("role deleted", null);
    }

    private static ResponseEntity<ApiResponse> ok(String msg, Object data) {
        return ResponseEntity.ok(new ApiResponse(true, msg, data, 200));
    }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
