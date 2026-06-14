package com.social.app.module.channel.util;

import com.social.app.common.ENVConfig;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for OAuth access tokens stored in the database.
 *
 * Why AES-GCM:
 *   • Authenticated encryption — detects tampering (GCM auth tag).
 *   • Unique IV per encrypt call — two identical tokens produce different ciphertext.
 *   • Zero external dependencies (javax.crypto ships with the JDK).
 *
 * Storage layout:  column access_token_enc = base64(ciphertext)
 *                  column token_iv         = base64(12-byte IV)
 *                  column token_tag        = base64(16-byte auth tag)  ← part of ciphertext in Java
 *
 * Note: Java's GCM implementation appends the 16-byte tag at the end of the
 * ciphertext array, so `token_tag` is extracted from the last 16 bytes of the
 * encrypted blob for record-keeping; decryption uses the full blob directly.
 *
 * Key source: CHANNEL_ENCRYPTION_KEY env var — must be a 64-char hex string
 * (32 bytes = 256 bits).  Generate once with:
 *   python3 -c "import secrets; print(secrets.token_hex(32))"
 */
public final class TokenEncryptionUtil {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_BYTES   = 12;   // 96-bit IV — GCM standard
    private static final int    TAG_BITS   = 128;  // 128-bit auth tag

    /** Decoded key — loaded once at class-init; fails fast if env var is missing or wrong length. */
    private static final SecretKey SECRET_KEY;

    static {
        String hex = ENVConfig.require("CHANNEL_ENCRYPTION_KEY");
        if (hex.length() != 64) {
            throw new IllegalStateException(
                "CHANNEL_ENCRYPTION_KEY must be a 64-character hex string (32 bytes / 256 bits). " +
                "Generate one with: python3 -c \"import secrets; print(secrets.token_hex(32))\"");
        }
        byte[] keyBytes = hexToBytes(hex);
        SECRET_KEY = new SecretKeySpec(keyBytes, "AES");
    }

    private TokenEncryptionUtil() {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Encrypts a plaintext token.
     * @return EncryptionResult with base64-encoded ciphertext, IV and tag.
     */
    public static EncryptionResult encrypt(String plaintext) {
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherWithTag = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Java appends the 16-byte GCM tag at the end — extract it for the separate column.
            int tagOffset = cipherWithTag.length - 16;
            byte[] tag = new byte[16];
            System.arraycopy(cipherWithTag, tagOffset, tag, 0, 16);

            return new EncryptionResult(
                Base64.getEncoder().encodeToString(cipherWithTag),
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(tag)
            );
        } catch (Exception e) {
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypts a token stored in the database.
     * @param encryptedB64  base64 ciphertext (includes GCM tag at end)
     * @param ivB64         base64 IV
     * @return plaintext token string
     */
    public static String decrypt(String encryptedB64, String ivB64) {
        try {
            byte[] cipherWithTag = Base64.getDecoder().decode(encryptedB64);
            byte[] iv            = Base64.getDecoder().decode(ivB64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(cipherWithTag);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Token decryption failed — key mismatch or data corrupt", e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static byte[] generateIv() {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // ─── Result record ───────────────────────────────────────────────────────

    public record EncryptionResult(
        String ciphertextB64,   // stored in access_token_enc column
        String ivB64,           // stored in token_iv column
        String tagB64           // stored in token_tag column
    ) {}
}
