package com.social.app.module.iam.config;

/**
 * Thin delegate to {@link com.social.app.common.ENVConfig}.
 * All new code should import the common class directly.
 */
public final class ENVConfig {
    private ENVConfig() { }

    public static String get(String key) {
        return com.social.app.common.ENVConfig.get(key);
    }

    public static String get(String key, String fallback) {
        return com.social.app.common.ENVConfig.get(key, fallback);
    }

    public static int getInt(String key, int fallback) {
        return com.social.app.common.ENVConfig.getInt(key, fallback);
    }

    public static boolean getBoolean(String key, boolean fallback) {
        return com.social.app.common.ENVConfig.getBoolean(key, fallback);
    }

    public static String require(String key) {
        return com.social.app.common.ENVConfig.require(key);
    }
}
