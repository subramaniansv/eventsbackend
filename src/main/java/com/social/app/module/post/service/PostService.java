package com.social.app.module.post.service;

import com.social.app.module.channel.models.Channel;
import com.social.app.module.channel.models.ChannelPlatform;
import com.social.app.module.channel.repository.ChannelRepository;
import com.social.app.module.channel.service.MetaGraphClient;
import com.social.app.module.channel.util.TokenEncryptionUtil;
import com.social.app.module.post.models.Post;
import com.social.app.module.post.models.PostStatus;
import com.social.app.module.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PostService {

    private static final Logger LOG = LoggerFactory.getLogger(PostService.class);

    private final PostRepository    postRepo;
    private final ChannelRepository channelRepo;
    private final MetaGraphClient   graph;

    public PostService(PostRepository postRepo,
                       ChannelRepository channelRepo,
                       MetaGraphClient graph) {
        this.postRepo    = postRepo;
        this.channelRepo = channelRepo;
        this.graph       = graph;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public Post create(Post post) {
        if (post.getStatus() == null) post.setStatus(PostStatus.DRAFT);
        return postRepo.save(post);
    }

    public List<Post> listByTenant(UUID tenantId) {
        return postRepo.findByTenant(tenantId);
    }

    public Optional<Post> getById(UUID postId, UUID tenantId) {
        return postRepo.findById(postId, tenantId);
    }

    public void delete(UUID postId, UUID tenantId) {
        postRepo.delete(postId, tenantId);
    }

    // ── Publishing ────────────────────────────────────────────────────────────

    /**
     * Immediately publishes a post to its connected channel.
     * Decrypts the stored page access token, calls Meta Graph API, then
     * updates the post status to PUBLISHED or FAILED.
     */
    public Post publish(UUID postId, UUID tenantId) {
        Post post = postRepo.findById(postId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        if (post.getStatus() == PostStatus.PUBLISHED)
            throw new IllegalStateException("Post is already published");
        if (post.getStatus() == PostStatus.PUBLISHING)
            throw new IllegalStateException("Post is currently being published");

        Channel channel = channelRepo.findById(post.getChannelId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found or not owned by tenant"));

        postRepo.markPublishing(postId);

        try {
            String platformPostId = publishToChannel(channel, post);
            postRepo.markPublished(postId, platformPostId);
            LOG.info("Post published: postId={} platformPostId={}", postId, platformPostId);
        } catch (Exception e) {
            String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            postRepo.markFailed(postId, reason);
            LOG.error("Post publish failed: postId={} reason={}", postId, reason);
            throw new RuntimeException("Publish failed: " + reason, e);
        }

        return postRepo.findById(postId, tenantId).orElse(post);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    String publishToChannel(Channel channel, Post post) throws Exception {
        String pageToken = TokenEncryptionUtil.decrypt(
                channel.getAccessTokenEnc(), channel.getTokenIv());

        if (channel.getPlatform() == ChannelPlatform.META_PAGE) {
            return graph.publishPagePost(channel.getPlatformId(), pageToken, post.getContent()).get();
        }

        if (channel.getPlatform() == ChannelPlatform.INSTAGRAM) {
            List<String> media = post.getMediaUrls();
            if (media == null || media.isEmpty())
                throw new IllegalArgumentException(
                        "Instagram requires at least one media URL — add a mediaUrls entry to your post");
            String imageUrl = media.get(0); // first image used for single post
            String caption  = post.getContent() != null ? post.getContent() : "";
            return graph.publishInstagramPost(channel.getPlatformId(), pageToken, caption, imageUrl).get();
        }

        throw new UnsupportedOperationException(
                "Publishing not yet supported for platform: " + channel.getPlatform());
    }
}
