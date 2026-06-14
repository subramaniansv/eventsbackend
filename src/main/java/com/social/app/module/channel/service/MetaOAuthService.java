package com.social.app.module.channel.service;

import com.social.app.common.ENVConfig;
import com.social.app.module.channel.models.*;
import com.social.app.module.channel.repository.ChannelRepository;
import com.social.app.module.channel.repository.OAuthStateRepository;
import com.social.app.module.channel.util.TokenEncryptionUtil;
import com.social.app.module.channel.util.TokenEncryptionUtil.EncryptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the Meta OAuth 2.0 flow.
 *
 * Flow:
 *  1. {@link #buildAuthorizationUrl} — generates a CSRF state, stores its hash,
 *     returns the Meta login URL.
 *  2. {@link #handleCallback} — validates the state, exchanges the code for tokens,
 *     fetches pages + Instagram accounts, encrypts tokens, saves Channel rows.
 *
 * OAuth scopes requested:
 *   - pages_show_list          — enumerate the user's Pages
 *   - pages_read_engagement    — read post/comment data
 *   - pages_manage_posts       — create / edit / delete posts
 *   - pages_read_user_content  — read user-generated content on Pages
 *   - instagram_basic          — basic Instagram account info
 *   - instagram_content_publish — publish to Instagram
 *   - business_management      — required by Facebook Login for Business
 */
@Service
public class MetaOAuthService {

    private static final Logger LOG = LoggerFactory.getLogger(MetaOAuthService.class);

    private static final String REDIRECT_URI = ENVConfig.require("META_REDIRECT_URI");
    private static final String APP_ID       = ENVConfig.require("META_APP_ID");

    private static final String SCOPES = String.join(",",
            "public_profile",             // identify the user
            "pages_show_list",            // list pages the user manages
            "pages_read_engagement",      // read page insights/metrics
            "instagram_basic",            // read Instagram account info
            "instagram_content_publish"   // publish to Instagram
    );

    private final MetaGraphClient     graph;
    private final ChannelRepository   channelRepo;
    private final OAuthStateRepository stateRepo;

    public MetaOAuthService(MetaGraphClient graph,
                            ChannelRepository channelRepo,
                            OAuthStateRepository stateRepo) {
        this.graph       = graph;
        this.channelRepo = channelRepo;
        this.stateRepo   = stateRepo;
    }

    // ── Step 1: generate the redirect URL ────────────────────────────────────

    /**
     * Creates a CSRF state token, stores its SHA-256 hash in the DB, and
     * returns the Meta authorization URL to redirect the user to.
     *
     * @param tenantId  the tenant initiating the connection
     * @param userId    the user clicking "Connect"
     * @return the URL to redirect the user's browser to
     */
    public String buildAuthorizationUrl(UUID tenantId, UUID userId) {
        String rawState = generateState();
        stateRepo.create(rawState, tenantId, userId, "META");

        return "https://www.facebook.com/v21.0/dialog/oauth"
                + "?client_id="     + enc(APP_ID)
                + "&redirect_uri="  + enc(REDIRECT_URI)
                + "&scope="         + enc(SCOPES)
                + "&state="         + enc(rawState)
                + "&response_type=code"
                + "&auth_type=rerequest";  // force page-selection step even on re-auth
    }

    // ── Step 2: handle the callback ───────────────────────────────────────────

    /**
     * Called from the OAuth callback endpoint.
     *
     * Steps:
     *  a) Validate + consume the CSRF state.
     *  b) Exchange the one-time code for a short-lived user token.
     *  c) Exchange for a 60-day long-lived user token.
     *  d) Fetch all Facebook Pages and save as META_PAGE channels.
     *  e) For each Page with an Instagram Business account, save an INSTAGRAM channel.
     *
     * @return a summary of what was saved (for logging / frontend display)
     * @throws IllegalArgumentException if the state is invalid / expired / already used
     */
    public CallbackResult handleCallback(String code, String rawState) {
        // a) Validate CSRF state
        OAuthState state = stateRepo.consumeByRawState(rawState)
                .orElseThrow(() -> new IllegalArgumentException(
                        "invalid, expired, or already-used OAuth state"));

        UUID tenantId = state.getTenantId();
        UUID userId   = state.getUserId();

        // b+c) Exchange code → short token → long token (blocking but acceptable here)
        String shortToken;
        try {
            shortToken = graph.exchangeCodeForToken(code, REDIRECT_URI).get();
        } catch (Exception e) {
            throw new RuntimeException("Code exchange failed: " + rootMessage(e), e);
        }

        String longToken;
        try {
            longToken = graph.getLongLivedUserToken(shortToken).get();
        } catch (Exception e) {
            throw new RuntimeException("Long-lived token exchange failed: " + rootMessage(e), e);
        }

        // Debug: confirm who this token belongs to and what permissions were granted
        try {
            graph.debugToken(longToken).get();
        } catch (Exception e) {
            LOG.warn("Token debug failed (non-fatal): {}", rootMessage(e));
        }

        // d+e) Fetch Pages + Instagram, save channels
        List<MetaGraphClient.PageData> pages;
        try {
            pages = graph.getPages(longToken).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Pages: " + rootMessage(e), e);
        }

        List<Channel> saved = new ArrayList<>();
        for (MetaGraphClient.PageData page : pages) {
            // Encrypt the page's permanent access token
            EncryptionResult enc = TokenEncryptionUtil.encrypt(page.accessToken);

            Channel ch = new Channel();
            ch.setTenantId(tenantId);
            ch.setConnectedBy(userId);
            ch.setPlatform(ChannelPlatform.META_PAGE);
            ch.setStatus(ChannelStatus.ACTIVE);
            ch.setPlatformId(page.id);
            ch.setName(page.name);
            ch.setPictureUrl(page.pictureUrl);
            ch.setAccessTokenEnc(enc.ciphertextB64());
            ch.setTokenIv(enc.ivB64());
            ch.setTokenTag(enc.tagB64());
            saved.add(channelRepo.save(ch));
            LOG.info("Saved META_PAGE channel: tenant={} pageId={} name={}", tenantId, page.id, page.name);

            // e) Check for linked Instagram account
            try {
                Optional<MetaGraphClient.InstagramData> igOpt =
                        graph.getInstagramAccount(page.id, page.accessToken).get();
                igOpt.ifPresent(ig -> {
                    // Instagram Business accounts use the page's token
                    EncryptionResult igEnc = TokenEncryptionUtil.encrypt(page.accessToken);
                    Channel igCh = new Channel();
                    igCh.setTenantId(tenantId);
                    igCh.setConnectedBy(userId);
                    igCh.setPlatform(ChannelPlatform.INSTAGRAM);
                    igCh.setStatus(ChannelStatus.ACTIVE);
                    igCh.setPlatformId(ig.id);
                    igCh.setName(ig.name);
                    igCh.setPictureUrl(ig.pictureUrl);
                    igCh.setAccessTokenEnc(igEnc.ciphertextB64());
                    igCh.setTokenIv(igEnc.ivB64());
                    igCh.setTokenTag(igEnc.tagB64());
                    igCh.setMetaPageId(page.id);
                    saved.add(channelRepo.save(igCh));
                    LOG.info("Saved INSTAGRAM channel: tenant={} igId={}", tenantId, ig.id);
                });
            } catch (Exception e) {
                // Non-fatal — page connected even if IG lookup fails
                LOG.warn("Instagram lookup failed for page {}: {}", page.id, rootMessage(e));
            }
        }

        return new CallbackResult(tenantId, saved.size(), pages.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String generateState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : t.getMessage();
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public record CallbackResult(
        UUID  tenantId,
        int   channelsSaved,
        int   pagesFound
    ) {}
}
