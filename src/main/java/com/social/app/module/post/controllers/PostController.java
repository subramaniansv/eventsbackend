package com.social.app.module.post.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import com.social.app.module.post.models.Post;
import com.social.app.module.post.models.PostStatus;
import com.social.app.module.post.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * POST /api/posts              — create a post (DRAFT or SCHEDULED)
 * GET  /api/posts              — list all posts for the tenant
 * GET  /api/posts/{id}         — get a single post
 * POST /api/posts/{id}/publish — immediately publish a post
 * DELETE /api/posts/{id}       — delete a DRAFT or FAILED post
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody JsonNode body) {
        AuthUser caller = AuthContext.get();
        if (caller == null) return err(401, "unauthorized");

        String content = field(body, "content");
        String channelIdStr = field(body, "channelId");
        if (channelIdStr == null) return err(400, "channelId required");

        Post post = new Post();
        post.setTenantId(caller.getTenantId());
        post.setCreatedBy(caller.getUserId());
        post.setContent(content);

        try {
            post.setChannelId(UUID.fromString(channelIdStr));
        } catch (IllegalArgumentException e) {
            return err(400, "invalid channelId");
        }

        // media URLs — optional array
        if (body.hasNonNull("mediaUrls") && body.get("mediaUrls").isArray()) {
            List<String> urls = new java.util.ArrayList<>();
            body.get("mediaUrls").forEach(n -> urls.add(n.asText()));
            post.setMediaUrls(urls);
        }

        // scheduledAt — if provided, mark as SCHEDULED, else DRAFT
        String scheduledAt = field(body, "scheduledAt");
        if (scheduledAt != null) {
            try {
                post.setScheduledAt(LocalDateTime.parse(scheduledAt));
                post.setStatus(PostStatus.SCHEDULED);
            } catch (Exception e) {
                return err(400, "invalid scheduledAt format, use ISO-8601 (e.g. 2026-06-10T14:00:00)");
            }
        } else {
            post.setStatus(PostStatus.DRAFT);
        }

        Post saved = service.create(post);
        return ok("post created", saved);
    }

    @GetMapping
    public ResponseEntity<ApiResponse> list() {
        AuthUser caller = AuthContext.get();
        if (caller == null) return err(401, "unauthorized");
        return ok("posts fetched", service.listByTenant(caller.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getOne(@PathVariable("id") String id) {
        AuthUser caller = AuthContext.get();
        if (caller == null) return err(401, "unauthorized");
        try {
            return service.getById(UUID.fromString(id), caller.getTenantId())
                    .map(p -> ok("post fetched", p))
                    .orElse(err(404, "post not found"));
        } catch (IllegalArgumentException e) {
            return err(400, "invalid post id");
        }
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse> publish(@PathVariable("id") String id) {
        AuthUser caller = AuthContext.get();
        if (caller == null) return err(401, "unauthorized");
        try {
            Post published = service.publish(UUID.fromString(id), caller.getTenantId());
            return ok("post published", published);
        } catch (IllegalArgumentException e) {
            return err(404, e.getMessage());
        } catch (IllegalStateException e) {
            return err(409, e.getMessage());
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return err(500, "publish failed: " + msg);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable("id") String id) {
        AuthUser caller = AuthContext.get();
        if (caller == null) return err(401, "unauthorized");
        try {
            service.delete(UUID.fromString(id), caller.getTenantId());
            return ok("post deleted", null);
        } catch (IllegalArgumentException e) {
            return err(400, "invalid post id");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String field(JsonNode body, String key) {
        if (body == null || !body.hasNonNull(key)) return null;
        String v = body.get(key).asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private static ResponseEntity<ApiResponse> ok(String msg, Object data) {
        return ResponseEntity.ok(new ApiResponse(true, msg, data, 200));
    }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
