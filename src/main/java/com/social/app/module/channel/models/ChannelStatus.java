package com.social.app.module.channel.models;

/**
 * Lifecycle status of a connected channel.
 *
 * ACTIVE       — token is valid, can publish
 * EXPIRED      — token expired (60-day user token — shouldn't happen for page tokens)
 * REVOKED      — user revoked app permissions on Meta side
 * DISCONNECTED — user manually disconnected the channel from our app
 */
public enum ChannelStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
    DISCONNECTED
}
