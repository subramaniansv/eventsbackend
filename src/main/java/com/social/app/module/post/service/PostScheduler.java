package com.social.app.module.post.service;

import com.social.app.module.channel.models.Channel;
import com.social.app.module.channel.repository.ChannelRepository;
import com.social.app.module.post.models.Post;
import com.social.app.module.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Background scheduler that polls for SCHEDULED posts whose scheduled_at
 * is in the past and publishes them to their respective channels.
 *
 * Runs every 60 seconds. Enable with @EnableScheduling in Main.java.
 */
@Component
public class PostScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PostScheduler.class);

    private final PostRepository    postRepo;
    private final ChannelRepository channelRepo;
    private final PostService       postService;

    public PostScheduler(PostRepository postRepo,
                         ChannelRepository channelRepo,
                         PostService postService) {
        this.postRepo    = postRepo;
        this.channelRepo = channelRepo;
        this.postService = postService;
    }

    @Scheduled(fixedDelay = 60_000)   // every 60 seconds
    public void publishDuePosts() {
        List<Post> due = postRepo.findDueScheduled();
        if (due.isEmpty()) return;

        LOG.info("PostScheduler: {} post(s) due for publishing", due.size());
        for (Post post : due) {
            try {
                Optional<Channel> chOpt = channelRepo.findById(post.getChannelId(), post.getTenantId());
                if (chOpt.isEmpty()) {
                    postRepo.markFailed(post.getPostId(), "Channel not found");
                    continue;
                }
                postRepo.markPublishing(post.getPostId());
                String platformPostId = postService.publishToChannel(chOpt.get(), post);
                postRepo.markPublished(post.getPostId(), platformPostId);
                LOG.info("Scheduled post published: postId={}", post.getPostId());
            } catch (Exception e) {
                String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                postRepo.markFailed(post.getPostId(), reason);
                LOG.error("Scheduled post failed: postId={} reason={}", post.getPostId(), reason);
            }
        }
    }
}
