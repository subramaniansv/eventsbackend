package com.social.app.module.iam.controllers;

import com.social.app.module.iam.config.ENVConfig;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import com.social.app.module.iam.services.EmailVerificationService;
import com.social.app.module.iam.services.EmailVerificationService.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Email verification endpoints.
 *
 *   GET  /api/email-verify?token=<raw>   public  — renders an HTML status page
 *   POST /api/email-verify/resend        bearer  — resend verification email
 */
@RestController
@RequestMapping("/api/email-verify")
public class EmailVerificationController {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationController.class);
    private final EmailVerificationService service = new EmailVerificationService();

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(required = false) String token) {
        Result result;
        try {
            result = service.verify(token);
        } catch (Exception e) {
            LOG.error("email verify exception", e);
            result = Result.INVALID;
        }
        return switch (result) {
            case VERIFIED    -> html(200, "Email verified",    "#0f5d3a",
                    "&#x2714; Your email has been verified",
                    "Thanks — your account is now fully active. You can close this tab.");
            case ALREADY_USED -> html(200, "Already verified", "#0f5d3a",
                    "Email already verified",
                    "This link has already been used. Your account is good to go.");
            case EXPIRED     -> html(410, "Link expired",     "#b45309",
                    "This verification link has expired",
                    "Sign in to your account and request a fresh verification email.");
            default          -> html(400, "Invalid link",     "#b91c1c",
                    "We couldn&#39;t verify that link",
                    "The link is incomplete or invalid. Try requesting a new one.");
        };
    }

    @PostMapping("/resend")
    public ResponseEntity<ApiResponse> resend() {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null)
            return ResponseEntity.status(401).body(new ApiResponse(false, "unauthorized", null, 401));
        try {
            boolean ok = service.resendForUser(caller.getUserId());
            if (ok) return ResponseEntity.ok(new ApiResponse(true, "verification email sent", null, 200));
            return ResponseEntity.status(400).body(
                    new ApiResponse(false, "verification not sent (already verified or no email)", null, 400));
        } catch (Exception e) {
            LOG.error("email verify resend exception", e);
            return ResponseEntity.status(500).body(
                    new ApiResponse(false, "could not resend verification email", null, 500));
        }
    }

    private ResponseEntity<String> html(int status, String title, String accent,
                                        String heading, String body) {
        String home = ENVConfig.get("APP_HOME_URL", "/");
        String page = "<!doctype html><html lang='en'><head>"
                + "<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + title + "</title><style>"
                + "body{margin:0;font-family:-apple-system,Segoe UI,Roboto,sans-serif;"
                + "background:#fafaf7;color:#1a1a1a;display:flex;align-items:center;"
                + "justify-content:center;min-height:100vh;padding:24px;}"
                + ".card{max-width:480px;width:100%;background:#fff;border:1px solid #eceae3;"
                + "border-radius:16px;padding:36px 32px;text-align:center;"
                + "box-shadow:0 6px 24px rgba(15,93,58,.06);}"
                + ".badge{display:inline-block;padding:6px 14px;border-radius:999px;font-size:12px;"
                + "font-weight:600;letter-spacing:.08em;text-transform:uppercase;"
                + "background:" + accent + "1a;color:" + accent + ";margin-bottom:18px;}"
                + "h1{margin:0 0 12px;font-size:22px;color:" + accent + ";}"
                + "p{margin:0 0 20px;line-height:1.6;color:#4b5563;font-size:15px;}"
                + ".cta{display:inline-block;padding:11px 22px;background:#0f5d3a;color:#fff;"
                + "text-decoration:none;border-radius:999px;font-weight:600;font-size:14px;}"
                + ".footer{margin-top:28px;color:#9ca3af;font-size:12px;}"
                + "</style></head><body><main class='card'>"
                + "<span class='badge'>App</span>"
                + "<h1>" + heading + "</h1><p>" + body + "</p>"
                + "<a class='cta' href='" + home + "'>Go to homepage</a>"
                + "<div class='footer'>Your social media management platform</div>"
                + "</main></body></html>";
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(page);
    }
}
