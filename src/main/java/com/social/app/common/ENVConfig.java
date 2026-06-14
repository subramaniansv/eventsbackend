package com.social.app.common;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Application-wide configuration reader.
 *
 * <p>Lookup order (first non-blank value wins):
 * <ol>
 *   <li>Process environment variable — always wins, so Render / VPS dashboard
 *       values can never be silently overridden by a stale .env file.</li>
 *   <li>.env file in the working directory — developer convenience for local
 *       runs. Ignored silently when the file is absent or malformed.</li>
 * </ol>
 *
 * <p>Required keys (the app refuses to start if any are missing):
 * <pre>
 *   DB_URL               jdbc:postgresql://host/db?sslmode=require
 *   DB_USERNAME
 *   DB_PASSWORD
 *   JWT_ACCESS_SECRET    at least 32 random characters
 *   JWT_REFRESH_SECRET   at least 32 random characters (different from above)
 *   APP_BASE_URL         public-facing API base, e.g. https://api.example.com
 *   APP_HOME_URL         frontend origin,          e.g. https://app.example.com
 *   GOOGLE_CLIENT_ID     OAuth 2.0 client id from Google Cloud Console
 * </pre>
 *
 * <p>Optional keys (have safe defaults):
 * <pre>
 *   APP_NAME             display name used in emails         (default: "App")
 *   DB_POOL_MAX          HikariCP max pool size              (default: 10)
 *   DB_POOL_MIN_IDLE     HikariCP min idle connections       (default: 2)
 *   MAIL_ENABLED         "false" to disable all mail         (default: true)
 *   MAIL_LOGIN_ALERTS    "true"  to email on every sign-in   (default: false)
 *   ZEPTOMAIL_TOKEN      preferred HTTP mail transport
 *   ZEPTOMAIL_URL        (default: https://api.zeptomail.in/v1.1/email)
 *   SMTP_FROM            From header for all outbound mail
 *   SMTP_HOST            fallback SMTP server hostname
 *   SMTP_PORT            (default: 587)
 *   SMTP_USERNAME
 *   SMTP_PASSWORD
 *   SMTP_STARTTLS        (default: true)
 *   SMTP_SSL             (default: false)
 * </pre>
 */
public final class ENVConfig {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()    // no .env in prod — fine
            .ignoreIfMalformed()  // skip "export FOO=bar" style lines
            .load();

    private ENVConfig() { }

    /**
     * Returns the value for {@code key}, or {@code null} when neither the
     * process environment nor the .env file has a value for it.
     */
    public static String get(String key) {
        // 1. Real process environment — set by hosting dashboard (Render, VPS).
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) return v.trim();

        // 2. .env file — developer convenience.
        v = DOTENV.get(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    /**
     * Convenience method that returns {@code fallback} instead of null when
     * the key is absent.
     */
    public static String get(String key, String fallback) {
        String v = get(key);
        return v != null ? v : fallback;
    }

    /**
     * Returns the value as an {@code int}. Uses {@code fallback} when the key
     * is missing or the value cannot be parsed.
     */
    public static int getInt(String key, int fallback) {
        String v = get(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Returns the value as a {@code boolean}. Only the exact string {@code "true"}
     * (case-insensitive) evaluates to {@code true}; everything else is {@code false}.
     */
    public static boolean getBoolean(String key, boolean fallback) {
        String v = get(key);
        if (v == null) return fallback;
        return "true".equalsIgnoreCase(v);
    }

    /**
     * Like {@link #get(String)} but throws {@link IllegalStateException} when
     * the key is missing. Use this for values that are required at startup so
     * the app fails fast with a clear message rather than later with an NPE.
     */
    public static String require(String key) {
        String v = get(key);
        if (v == null) {
            throw new IllegalStateException(
                    "Required environment variable '" + key + "' is not set. "
                    + "Add it to the hosting dashboard (Render / VPS) or to your local .env file.");
        }
        return v;
    }
}
