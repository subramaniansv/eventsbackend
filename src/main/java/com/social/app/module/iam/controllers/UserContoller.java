package com.social.app.module.iam.controllers;

import com.social.app.module.iam.models.*;
import com.social.app.module.iam.security.RequiresRole;
import com.social.app.module.iam.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.UUID;

/**
 * Admin user management (requires "Admin" role).
 *
 *   POST /api/user                             change a user's password
 *   GET  /api/user                             list all users
 *   GET  /api/user?userId=<uuid>               fetch one user
 *   PUT  /api/user?userId=<uuid>&status=ACTIVE activate / suspend a user
 */
@RestController
@RequestMapping("/api/user")
@RequiresRole("Admin")
public class UserContoller {

    private final UserService service = new UserService();

    @PostMapping
    public ResponseEntity<ApiResponse> changePassword(
            @RequestBody PasswordResetRequest ps) {
        boolean ok = service.updatePassword(ps);
        return ResponseEntity.ok(new ApiResponse(ok, "password reset", null, 200));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getUsers(
            @RequestParam(required = false) String userId) {
        if (userId != null) {
            User user = service.getUser(userId);
            return ResponseEntity.ok(new ApiResponse(true, "user fetched", user, 200));
        }
        return ResponseEntity.ok(new ApiResponse(true, "users fetched", service.getAllUsers(), 200));
    }

    @PutMapping
    public ResponseEntity<ApiResponse> updateStatus(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status) {

        if (blank(userId) || blank(status))
            return err(400, "userId and status are required");
        try { UUID.fromString(userId); } catch (IllegalArgumentException e) {
            return err(400, "invalid userId");
        }
        try { UserStatus.valueOf(status.toUpperCase()); } catch (IllegalArgumentException e) {
            return err(400, "invalid status; allowed: " + Arrays.toString(UserStatus.values()));
        }
        boolean ok = service.updateStatus(userId, status.toUpperCase());
        return ResponseEntity.ok(new ApiResponse(ok, "user status updated", null, 200));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
