package com.social.app.module.channel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.common.ENVConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Thin async wrapper around the Meta Graph API v21.0.
 *
 * Uses Java 11's built-in HttpClient with no external dependencies.
 * All methods are async (CompletableFuture) so they don't block
 * the calling thread during token exchange or API calls.
 */
@Component
public class MetaGraphClient {

    private static final Logger LOG = LoggerFactory.getLogger(MetaGraphClient.class);

    /** Latest stable Graph API version. Update here when Meta announces deprecation. */
    private static final String GRAPH = "https://graph.facebook.com/v21.0";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Credentials ──────────────────────────────────────────────────────────

    private static final String APP_ID     = ENVConfig.require("META_APP_ID");
    private static final String APP_SECRET = ENVConfig.require("META_APP_SECRET");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Exchange the one-time authorization code for a short-lived user access token.
     * The token is valid for ~1 hour.
     */
    public CompletableFuture<String> exchangeCodeForToken(String code, String redirectUri) {
        String url = GRAPH + "/oauth/access_token"
                + "?client_id="     + enc(APP_ID)
                + "&client_secret=" + enc(APP_SECRET)
                + "&redirect_uri="  + enc(redirectUri)
                + "&code="          + enc(code);

        return getAsync(url).thenApply(json -> {
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank())
                throw new RuntimeException("Meta code exchange failed: " + json.toString());
            LOG.debug("Short-lived user token obtained");
            return token;
        });
    }

    /**
     * Exchange a short-lived user token for a long-lived token (~60 days).
     * Only user tokens can be extended this way; Page tokens are already long-lived.
     */
    public CompletableFuture<String> getLongLivedUserToken(String shortToken) {
        String url = GRAPH + "/oauth/access_token"
                + "?grant_type=fb_exchange_token"
                + "&client_id="     + enc(APP_ID)
                + "&client_secret=" + enc(APP_SECRET)
                + "&fb_exchange_token=" + enc(shortToken);

        return getAsync(url).thenApply(json -> {
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank())
                throw new RuntimeException("Long-lived token exchange failed: " + json.toString());
            LOG.debug("Long-lived user token obtained");
            return token;
        });
    }

    /**
     * Fetches all Facebook Pages managed by the authenticated user.
     * Returns a list of {@link PageData} with the page's permanent page access token.
     *
     * @param longLivedUserToken the 60-day user token
     */
    public CompletableFuture<List<PageData>> getPages(String longLivedUserToken) {
        String url = GRAPH + "/me/accounts"
                + "?fields=id,name,picture%7Burl%7D,access_token"
                + "&access_token=" + enc(longLivedUserToken);

        return getAsync(url).thenApply(json -> {
            LOG.info("getPages raw response: {}", json);
            List<PageData> pages = new ArrayList<>();
            JsonNode data = json.path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    PageData p = new PageData();
                    p.id          = node.path("id").asText();
                    p.name        = node.path("name").asText();
                    p.accessToken = node.path("access_token").asText();
                    JsonNode pic  = node.path("picture").path("data").path("url");
                    p.pictureUrl  = pic.isMissingNode() ? null : pic.asText();
                    pages.add(p);
                }
            }
            LOG.info("Fetched {} pages", pages.size());
            return pages;
        });
    }

    /**
     * Returns the Instagram Business or Creator account connected to a Facebook Page,
     * if one exists.
     *
     * @param pageId        the Facebook Page ID
     * @param pageToken     the Page's permanent access token
     */
    public CompletableFuture<Optional<InstagramData>> getInstagramAccount(
            String pageId, String pageToken) {

        String url = GRAPH + "/" + enc(pageId)
                + "?fields=instagram_business_account%7Bid%2Cname%2Cusername%2Cprofile_picture_url%7D"
                + "&access_token=" + enc(pageToken);

        return getAsync(url).thenApply(json -> {
            LOG.info("getInstagramAccount raw response for pageId={}: {}", pageId, json);
            JsonNode ib = json.path("instagram_business_account");
            if (ib.isMissingNode() || ib.isNull()) return Optional.empty();
            InstagramData ig = new InstagramData();
            ig.id         = ib.path("id").asText();
            ig.name       = ib.path("name").asText(ib.path("username").asText());
            ig.pictureUrl = ib.path("profile_picture_url").asText(null);
            return Optional.of(ig);
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Publishes a text post to a Facebook Page feed.
     * Returns the platform-assigned post ID (e.g. "1234_5678").
     *
     * @param pageId       the Facebook Page ID
     * @param pageToken    the Page's permanent access token (decrypted)
     * @param message      post text content
     * @return platform post ID
     */
    public CompletableFuture<String> publishPagePost(String pageId, String pageToken, String message) {
        String url = GRAPH + "/" + enc(pageId) + "/feed";
        String body = "message=" + enc(message) + "&access_token=" + enc(pageToken);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                   .thenApply(res -> {
                       try {
                           JsonNode json = MAPPER.readTree(res.body());
                           JsonNode err  = json.get("error");
                           if (err != null && !err.isNull())
                               throw new RuntimeException("Meta publish error: " + err.path("message").asText(res.body()));
                           String id = json.path("id").asText(null);
                           if (id == null || id.isBlank())
                               throw new RuntimeException("Meta publish returned no post id: " + res.body());
                           LOG.info("Published post to page={} platformPostId={}", pageId, id);
                           return id;
                       } catch (RuntimeException e) {
                           throw e;
                       } catch (Exception e) {
                           throw new RuntimeException("Failed to parse Meta publish response: " + e.getMessage(), e);
                       }
                   });
    }

    /**
     * Publishes an image post to an Instagram Business account.
     * Instagram requires at least one media URL — text-only posts are not supported.
     *
     * Step 1: Create a media container (returns creation_id).
     * Step 2: Publish the container (returns platform media id).
     *
     * @param igUserId   the Instagram Business account ID (platform_id from Channel)
     * @param pageToken  the parent Facebook Page's access token (decrypted)
     * @param caption    post caption / text
     * @param imageUrl   publicly accessible image URL
     * @return platform media ID
     */
    public CompletableFuture<String> publishInstagramPost(
            String igUserId, String pageToken, String caption, String imageUrl) {

        // Step 1 — create media container
        String containerUrl = GRAPH + "/" + enc(igUserId) + "/media";
        String containerBody = "media_type=IMAGE"
                + "&image_url=" + enc(imageUrl)
                + "&caption=" + enc(caption)
                + "&access_token=" + enc(pageToken);

        HttpRequest containerReq = HttpRequest.newBuilder()
                .uri(URI.create(containerUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(containerBody))
                .build();

        return HTTP.sendAsync(containerReq, HttpResponse.BodyHandlers.ofString())
                   .thenCompose(res -> {
                       try {
                           JsonNode json = MAPPER.readTree(res.body());
                           JsonNode err  = json.get("error");
                           if (err != null && !err.isNull())
                               throw new RuntimeException("Instagram container error: " + err.path("message").asText(res.body()));
                           String creationId = json.path("id").asText(null);
                           if (creationId == null || creationId.isBlank())
                               throw new RuntimeException("Instagram container returned no id: " + res.body());
                           LOG.debug("Instagram media container created: creationId={}", creationId);

                           // Step 2 — publish the container
                           String publishUrl  = GRAPH + "/" + enc(igUserId) + "/media_publish";
                           String publishBody = "creation_id=" + enc(creationId) + "&access_token=" + enc(pageToken);

                           HttpRequest publishReq = HttpRequest.newBuilder()
                                   .uri(URI.create(publishUrl))
                                   .timeout(Duration.ofSeconds(30))
                                   .header("Content-Type", "application/x-www-form-urlencoded")
                                   .POST(HttpRequest.BodyPublishers.ofString(publishBody))
                                   .build();

                           return HTTP.sendAsync(publishReq, HttpResponse.BodyHandlers.ofString())
                                      .thenApply(pubRes -> {
                                          try {
                                              JsonNode pubJson = MAPPER.readTree(pubRes.body());
                                              JsonNode pubErr  = pubJson.get("error");
                                              if (pubErr != null && !pubErr.isNull())
                                                  throw new RuntimeException("Instagram publish error: " + pubErr.path("message").asText(pubRes.body()));
                                              String mediaId = pubJson.path("id").asText(null);
                                              if (mediaId == null || mediaId.isBlank())
                                                  throw new RuntimeException("Instagram publish returned no media id: " + pubRes.body());
                                              LOG.info("Published post to instagram igUserId={} mediaId={}", igUserId, mediaId);
                                              return mediaId;
                                          } catch (RuntimeException e) {
                                              throw e;
                                          } catch (Exception e) {
                                              throw new RuntimeException("Failed to parse Instagram publish response", e);
                                          }
                                      });
                       } catch (RuntimeException e) {
                           throw e;
                       } catch (Exception e) {
                           throw new RuntimeException("Failed to parse Instagram container response", e);
                       }
                   });
    }

    /**
     * Logs who the token belongs to and what permissions it carries.
     * Used during OAuth callback for diagnosing empty /me/accounts responses.
     */
    public CompletableFuture<Void> debugToken(String userToken) {
        String meUrl = GRAPH + "/me?fields=id,name&access_token=" + enc(userToken);
        String permUrl = GRAPH + "/me/permissions?access_token=" + enc(userToken);

        return getAsync(meUrl).thenCompose(me -> {
            LOG.info("TOKEN DEBUG — me: id={} name={}", me.path("id").asText(), me.path("name").asText());
            return getAsync(permUrl);
        }).thenAccept(perms -> {
            StringBuilder sb = new StringBuilder("TOKEN DEBUG — permissions: ");
            if (perms.path("data").isArray()) {
                perms.path("data").forEach(p ->
                    sb.append(p.path("permission").asText()).append("=").append(p.path("status").asText()).append(" "));
            }
            LOG.info("{}", sb);
        });
    }

    private CompletableFuture<JsonNode> getAsync(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                   .thenApply(res -> {
                       try {
                           JsonNode json = MAPPER.readTree(res.body());
                           JsonNode err  = json.get("error");
                           if (err != null && !err.isNull()) {
                               throw new RuntimeException(
                                   "Meta API error: " + err.path("message").asText(res.body()));
                           }
                           return json;
                       } catch (RuntimeException e) {
                           throw e;
                       } catch (Exception e) {
                           throw new RuntimeException("Failed to parse Meta response: " + e.getMessage(), e);
                       }
                   });
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    public static class PageData {
        public String id;
        public String name;
        public String pictureUrl;
        public String accessToken;  // long-lived page token — never return in API responses
    }

    public static class InstagramData {
        public String id;
        public String name;
        public String pictureUrl;
        // Instagram accounts share the parent page's access token.
    }
}
