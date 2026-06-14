package com.social.app.module.mail;

import com.social.app.common.ENVConfig;

/**
 * Pre-baked HTML templates for the transactional emails we send.
 *
 * Each helper returns a complete &lt;!doctype html&gt; document with
 * inline styles - email clients strip &lt;style&gt; blocks aggressively
 * so inline is the only safe option.
 */
public final class MailTemplates {

    private MailTemplates() { }

    /** Read once at class-load time. Falls back to "App" so startup never fails. */
    private static final String APP_NAME = ENVConfig.get("APP_NAME", "App");
    /** Frontend base URL used in CTA buttons (e.g. https://app.example.com). Falls back to "#". */
    private static final String APP_HOME = strip(ENVConfig.get("APP_HOME_URL", "#"));

    private static final String BRAND_GREEN = "#0f5d3a";
    private static final String BRAND_BG    = "#fafaf7";
    private static final String BRAND_TEXT  = "#1a1a1a";
    private static final String BRAND_MUTED = "#6b7280";

    private static String strip(String url) {
        if (url == null) return "#";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /* ------------------ Welcome on registration ------------------ */
    public static String welcome(String firstName) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:22px;color:" + BRAND_TEXT + ";'>Welcome to " + APP_NAME + ", " + name + "!</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "We're thrilled to have you on board."
                + "</p>"
                + "<p style='margin:0 0 20px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Head over to the app and get started."
                + "</p>"
                + cta("Open app", APP_HOME);
        return wrap("Welcome to " + APP_NAME, body);
    }

    /* ------------------ Login alert ------------------ */
    public static String loginAlert(String firstName, String ip, String userAgent) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>New sign-in to your " + APP_NAME + " account</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", we noticed a new sign-in just now."
                + "</p>"
                + "<table style='border-collapse:collapse;margin:0 0 16px;'>"
                + row("IP address", ip == null ? "unknown" : ip)
                + row("Device",     userAgent == null ? "unknown" : userAgent)
                + "</table>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If this wasn't you, please reset your password immediately."
                + "</p>";
        return wrap("New sign-in to your account", body);
    }

    /* ------------------ Email verification ------------------ */
    public static String emailVerification(String firstName, String verifyUrl) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Verify your email</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", please confirm your email address to finish setting up your " + APP_NAME + " account."
                + "</p>"
                + cta("Verify email", verifyUrl)
                + "<p style='margin:16px 0 0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If the button doesn't work, paste this link into your browser:<br>"
                + "<span style='word-break:break-all;'>" + escape(verifyUrl) + "</span>"
                + "</p>";
        return wrap("Verify your " + APP_NAME + " email", body);
    }

    /* ------------------ Password reset ------------------ */
    public static String passwordReset(String firstName, String resetUrl) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Reset your password</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", click the button below to set a new password. The link is valid for 30 minutes."
                + "</p>"
                + cta("Reset password", resetUrl)
                + "<p style='margin:16px 0 0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Didn't ask for this? You can safely ignore this email."
                + "</p>";
        return wrap("Reset your " + APP_NAME + " password", body);
    }

    /* ------------------ Order placed (placeholder - order module not linked) ------------------ */
    public static String orderPlaced(String firstName, Object order) {
        return "";
    }

    /* ------------------ Order status update (placeholder - order module not linked) ------------------ */
    public static String orderStatusUpdate(String firstName, Object order, String newStatus) {
        return "";
    }

    /* ------------------ Password changed ------------------ */
    public static String passwordChanged(String firstName) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Your password was changed</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", this is a confirmation that the password for your " + APP_NAME + " account was just changed."
                + "</p>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If you didn't do this, please contact our support team right away."
                + "</p>";
        return wrap("Your " + APP_NAME + " password was changed", body);
    }

    /* ------------------ Account status change ------------------ */
    public static String accountStatusChanged(String firstName, String status) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String s = status == null ? "" : status.toUpperCase();
        String detail;
        switch (s) {
            case "ACTIVE":     detail = "Your " + APP_NAME + " account has been activated."; break;
            case "SUSPENDED":  detail = "Your " + APP_NAME + " account has been suspended. Please contact support for more information."; break;
            case "INACTIVE":   detail = "Your " + APP_NAME + " account has been marked inactive."; break;
            default:           detail = "Your " + APP_NAME + " account status is now " + escape(s) + ".";
        }
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Account status updated</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>Hi " + name + ",</p>"
                + "<p style='margin:0 0 16px;line-height:1.6;color:" + BRAND_TEXT + ";'>" + detail + "</p>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Status: <strong style='color:" + BRAND_TEXT + ";'>" + escape(s) + "</strong>"
                + "</p>";
        return wrap(APP_NAME + " account status updated", body);
    }

    /* ------------------ Contact form acknowledgement ------------------ */
    public static String contactAck(String firstName, String subjectLine, String message) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String subj = (subjectLine == null || subjectLine.isBlank()) ? "your message" : escape(subjectLine);
        String msg  = (message == null || message.isBlank()) ? "" : escape(message);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:22px;color:" + BRAND_TEXT + ";'>Thanks for reaching out, " + name + "!</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "We've received your message and our team will get in touch with you shortly."
                + "</p>"
                + "<p style='margin:0 0 16px;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Subject: <strong style='color:" + BRAND_TEXT + ";'>" + subj + "</strong>"
                + "</p>"
                + (msg.isEmpty() ? "" :
                    "<div style='margin:0 0 20px;padding:16px;background:" + BRAND_BG + ";border-left:3px solid "
                    + BRAND_GREEN + ";border-radius:4px;font-size:14px;color:" + BRAND_TEXT
                    + ";white-space:pre-line;line-height:1.6;'>" + msg + "</div>")
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If you need to add anything, just reply to this email and we'll see it."
                + "</p>";
        return wrap("We received your message", body);
    }

    /* ------------------ Contact form internal notification ------------------ */
    public static String contactNotify(String name, String fromEmail, String phone,
                                       String subjectLine, String message) {
        String safeName    = escape(name == null ? "(unknown)" : name);
        String safeEmail   = escape(fromEmail == null ? "(unknown)" : fromEmail);
        String safePhone   = (phone == null || phone.isBlank()) ? "-" : escape(phone);
        String safeSubject = (subjectLine == null || subjectLine.isBlank()) ? "(no subject)" : escape(subjectLine);
        String safeMessage = escape(message == null ? "" : message);

        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>New contact-form submission</h1>"
                + "<table style='border-collapse:collapse;margin:0 0 16px;'>"
                + row("From",    safeName)
                + row("Email",   safeEmail)
                + row("Phone",   safePhone)
                + row("Subject", safeSubject)
                + "</table>"
                + "<div style='margin:0 0 12px;padding:16px;background:" + BRAND_BG + ";border-left:3px solid "
                + BRAND_GREEN + ";border-radius:4px;font-size:14px;color:" + BRAND_TEXT
                + ";white-space:pre-line;line-height:1.6;'>" + safeMessage + "</div>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Reply directly to <a href='mailto:" + safeEmail + "' style='color:" + BRAND_GREEN
                + ";'>" + safeEmail + "</a> to respond to the customer."
                + "</p>";
        return wrap("New contact-form submission", body);
    }

    /* ------------------ Generic admin-composed mail ------------------ */
    public static String custom(String content) {
        // The admin/custom endpoint passes user-supplied content as-is so it
        // can contain HTML. We only wrap it in the brand chrome.
        return wrap(APP_NAME, content);
    }

    /* ------------------ shared chrome ------------------ */

    private static String wrap(String previewTitle, String contentHtml) {
        return "<!doctype html><html><head>"
                + "<meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + escape(previewTitle) + "</title>"
                + "</head>"
                + "<body style='margin:0;padding:0;background:" + BRAND_BG + ";font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:"
                + BRAND_TEXT + ";'>"
                + "<table role='presentation' width='100%' style='background:" + BRAND_BG + ";padding:32px 12px;'><tr><td align='center'>"
                + "<table role='presentation' width='560' style='max-width:560px;width:100%;background:#fff;border:1px solid #eceae3;border-radius:12px;overflow:hidden;'>"
                + "<tr><td style='padding:20px 28px;background:" + BRAND_GREEN + ";color:#fff;font-size:18px;font-weight:600;letter-spacing:.01em;'>"
                + APP_NAME
                + "</td></tr>"
                + "<tr><td style='padding:28px;'>"
                + contentHtml
                + "</td></tr>"
                + "<tr><td style='padding:16px 28px;background:" + BRAND_BG + ";color:" + BRAND_MUTED + ";font-size:12px;text-align:center;border-top:1px solid #eceae3;'>"
                + "&copy; " + APP_NAME
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    private static String cta(String label, String url) {
        return "<a href='" + escape(url) + "' "
                + "style='display:inline-block;padding:12px 22px;background:" + BRAND_GREEN + ";color:#fff;"
                + "text-decoration:none;border-radius:999px;font-weight:600;font-size:14px;'>"
                + escape(label)
                + "</a>";
    }

    private static String row(String key, String value) {
        return "<tr>"
                + "<td style='padding:4px 16px 4px 0;color:" + BRAND_MUTED + ";font-size:13px;'>" + escape(key) + "</td>"
                + "<td style='padding:4px 0;font-size:13px;color:" + BRAND_TEXT + ";'>" + escape(value) + "</td>"
                + "</tr>";
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /** Minimal HTML escape - prevents email injection of stray tags / entities. */
    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
