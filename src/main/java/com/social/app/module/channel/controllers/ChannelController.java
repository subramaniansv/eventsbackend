package com.social.app.module.channel.controllers;

import com.social.app.module.channel.models.Channel;
import com.social.app.module.channel.models.ChannelPlatform;
import com.social.app.module.channel.models.ChannelStatus;
import com.social.app.module.channel.repository.ChannelRepository;
import com.social.app.module.channel.service.MetaGraphClient;
import com.social.app.module.channel.util.TokenEncryptionUtil;
import com.social.app.module.channel.util.TokenEncryptionUtil.EncryptionResult;
import com.social.app.module.iam.models.ApiResponse;
import com.social.app.module.iam.security.AuthContext;
import com.social.app.module.iam.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API for connected social media channels.
 *
 *   GET    /api/channels           list all ACTIVE channels for the current tenant
 *   DELETE /api/channels/{id}      soft-delete (mark DISCONNECTED)
 *
 * All endpoints require an authenticated user with a tenant scope.
 * Access tokens are @JsonIgnore on the Channel model — they never appear
 * in API responses.
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelController.class);

    private final ChannelRepository channelRepo;
    private final MetaGraphClient   graph;

    public ChannelController(ChannelRepository channelRepo, MetaGraphClient graph) {
        this.channelRepo = channelRepo;
        this.graph       = graph;
    }

    // ── GET /api/channels ─────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse> listChannels() {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getTenantId() == null)
            return err(401, "authentication required");

        try {
            List<Channel> channels = channelRepo.findActiveByTenant(caller.getTenantId());
            return ok("channels fetched", channels);
        } catch (Exception e) {
            LOG.error("listChannels failed for tenant {}", caller.getTenantId(), e);
            return err(500, "could not fetch channels");
        }
    }

    // ── DELETE /api/channels/{id} ─────────────────────────────────────────────

    @DeleteMapping("/{channelId}")
    public ResponseEntity<ApiResponse> disconnect(@PathVariable UUID channelId) {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getTenantId() == null)
            return err(401, "authentication required");

        // Verify the channel belongs to this tenant before disconnecting.
        Optional<Channel> ch = channelRepo.findById(channelId, caller.getTenantId());
        if (ch.isEmpty()) return err(404, "channel not found");

        try {
            channelRepo.disconnect(channelId, caller.getTenantId());
            LOG.info("Channel {} disconnected by user {} (tenant {})",
                    channelId, caller.getUserId(), caller.getTenantId());
            return ok("channel disconnected", null);
        } catch (Exception e) {
            LOG.error("disconnect failed for channel {}", channelId, e);
            return err(500, "could not disconnect channel");
        }
    }

    // ── POST /api/channels/{id}/discover-instagram ────────────────────────────
    // Uses the page token already stored for a META_PAGE channel to look up
    // and save the linked Instagram Business account — no re-auth needed.

    @PostMapping("/{channelId}/discover-instagram")
    public ResponseEntity<ApiResponse> discoverInstagram(@PathVariable UUID channelId) {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getTenantId() == null)
            return err(401, "authentication required");

        Optional<Channel> opt = channelRepo.findById(channelId, caller.getTenantId());
        if (opt.isEmpty()) return err(404, "channel not found");

        Channel page = opt.get();
        if (page.getPlatform() != ChannelPlatform.META_PAGE)
            return err(400, "channel is not a META_PAGE");

        try {
            String pageToken = TokenEncryptionUtil.decrypt(page.getAccessTokenEnc(), page.getTokenIv());
            MetaGraphClient.InstagramData ig = graph.getInstagramAccount(page.getPlatformId(), pageToken).get()
                    .orElse(null);

            if (ig == null)
                return err(404, "no Instagram Business account linked to this page — go to Instagram Settings → Account → Switch to Professional, then link to the Testtvl Facebook Page");

            EncryptionResult enc = TokenEncryptionUtil.encrypt(pageToken);
            Channel igCh = new Channel();
            igCh.setTenantId(page.getTenantId());
            igCh.setConnectedBy(caller.getUserId());
            igCh.setPlatform(ChannelPlatform.INSTAGRAM);
            igCh.setStatus(ChannelStatus.ACTIVE);
            igCh.setPlatformId(ig.id);
            igCh.setName(ig.name);
            igCh.setPictureUrl(ig.pictureUrl);
            igCh.setAccessTokenEnc(enc.ciphertextB64());
            igCh.setTokenIv(enc.ivB64());
            igCh.setTokenTag(enc.tagB64());
            igCh.setMetaPageId(page.getPlatformId());
            Channel saved = channelRepo.save(igCh);
            LOG.info("Discovered INSTAGRAM channel: pageId={} igId={}", page.getPlatformId(), ig.id);
            return ok("instagram channel saved", saved);
        } catch (Exception e) {
            LOG.error("discover-instagram failed for channel {}", channelId, e);
            return err(500, "instagram discovery failed: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResponseEntity<ApiResponse> ok(String msg, Object data) {
        return ResponseEntity.ok(new ApiResponse(true, msg, data, 200));
    }

    private static ResponseEntity<ApiResponse> err(int status, String msg) {
        return ResponseEntity.status(status).body(new ApiResponse(false, msg, null, status));
    }
}
