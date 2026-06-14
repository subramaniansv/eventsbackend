package com.social.app.module.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.RequiresRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only endpoint for sending an ad-hoc email.
 *
 *   POST /api/mail
 *   Authorization: Bearer <admin token>
 *   { "to": "...", "subject": "...", "content": "...", "html": true }
 */
@RestController
@RequestMapping("/api/mail")
@RequiresRole("Admin")
public class MailController {

    private static final Logger LOG = LoggerFactory.getLogger(MailController.class);

    @PostMapping
    public ResponseEntity<ApiResponse> send(
            @RequestBody(required = false) JsonNode body) {

        if (body == null || body.isNull() || body.isMissingNode())
            return err(400, "request body is required");

        String to      = text(body, "to");
        String subject = text(body, "subject");
        String content = text(body, "content");
        if (content == null) content = text(body, "body");
        boolean html   = !body.has("html") || body.path("html").asBoolean(true);

        if (blank(to) || blank(subject) || blank(content))
            return err(400, "to, subject and content are required");

        boolean raw = body.path("raw").asBoolean(false);
        String finalBody = (raw || !html) ? content : MailTemplates.custom(content);

        try {
            MailService.get().sendNow(new MailMessage(to, subject, finalBody, html));
            return ResponseEntity.ok(new ApiResponse(true, "mail sent", null, 200));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(new ApiResponse(false, e.getMessage(), null, 503));
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        } catch (Exception e) {
            LOG.warn("admin mail send failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(
                    new ApiResponse(false, "mail delivery failed: " + e.getMessage(), null, 502));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
