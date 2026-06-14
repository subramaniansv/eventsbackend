-- ============================================================================
-- IAM Database Schema  —  Multi-Tenant
-- Updated: 2026-06-07
-- Hierarchy: SUPER_ADMIN (platform) → ORG_ADMIN → ORG_MEMBER
-- ============================================================================

-- ============================================================================
-- BASE TABLES (no FK dependencies)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Table: users  (global identity — one row per person across all orgs)
-- Notes:
--   • google_sub = Google's unique "sub" claim; used for Google sign-in matching.
--   • is_admin removed; admin status is conveyed through roles.
--   • password_hash = 'oauth:google' sentinel for Google-only accounts.
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    user_id        UUID    NOT NULL DEFAULT gen_random_uuid(),
    email          VARCHAR NOT NULL,
    password_hash  VARCHAR NOT NULL,
    first_name     VARCHAR,
    last_name      VARCHAR,
    status         VARCHAR,
    google_sub     VARCHAR,
    created_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    email_verified_at TIMESTAMP WITHOUT TIME ZONE,
    last_login     TIMESTAMP WITH TIME ZONE,

    CONSTRAINT users_pkey          PRIMARY KEY (user_id),
    CONSTRAINT users_email_key     UNIQUE (email),
    CONSTRAINT users_google_sub_key UNIQUE (google_sub)
);

-- ----------------------------------------------------------------------------
-- Table: tenants  (organisations / workspaces)
-- status: TRIAL | ACTIVE | SUSPENDED
-- plan  : FREE | PRO | ENTERPRISE
-- ----------------------------------------------------------------------------
CREATE TABLE tenants (
    tenant_id  UUID    NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR NOT NULL,
    slug       VARCHAR NOT NULL,
    status     VARCHAR NOT NULL DEFAULT 'TRIAL',
    plan       VARCHAR NOT NULL DEFAULT 'FREE',
    owner_id   UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tenants_pkey     PRIMARY KEY (tenant_id),
    CONSTRAINT tenants_slug_key UNIQUE (slug),
    CONSTRAINT tenants_owner_fkey FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- ----------------------------------------------------------------------------
-- Table: role  (NULL tenant_id = system-level role, e.g. SUPER_ADMIN)
-- ----------------------------------------------------------------------------
CREATE TABLE role (
    role_id     BIGSERIAL NOT NULL,
    name        VARCHAR,
    description TEXT,
    tenant_id   UUID,

    CONSTRAINT role_pkey       PRIMARY KEY (role_id),
    CONSTRAINT role_tenant_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Table: permission
-- ----------------------------------------------------------------------------
CREATE TABLE permission (
    permission_id BIGSERIAL NOT NULL,
    name          VARCHAR,
    description   TEXT,
    resource      VARCHAR,
    action        VARCHAR,

    CONSTRAINT permission_pkey PRIMARY KEY (permission_id)
);

-- ============================================================================
-- TABLES WITH FOREIGN KEY DEPENDENCIES
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Table: tenant_members  (user ↔ tenant membership)
-- status: ACTIVE | INVITED | SUSPENDED
-- ----------------------------------------------------------------------------
CREATE TABLE tenant_members (
    tenant_id UUID    NOT NULL,
    user_id   UUID    NOT NULL,
    status    VARCHAR NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tenant_members_pkey        PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT tenant_members_tenant_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT tenant_members_user_fkey   FOREIGN KEY (user_id)   REFERENCES users(user_id)    ON DELETE CASCADE
);

CREATE INDEX idx_tenant_members_user ON tenant_members USING btree (user_id);

-- ----------------------------------------------------------------------------
-- Table: tenant_invites  (email invitations sent by org admins)
-- ----------------------------------------------------------------------------
CREATE TABLE tenant_invites (
    invite_id   UUID    NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID    NOT NULL,
    email       VARCHAR NOT NULL,
    role_id     BIGINT,
    invited_by  UUID,
    token_hash  VARCHAR NOT NULL,
    expires_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITHOUT TIME ZONE,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tenant_invites_pkey           PRIMARY KEY (invite_id),
    CONSTRAINT tenant_invites_token_hash_key UNIQUE (token_hash),
    CONSTRAINT tenant_invites_tenant_fkey    FOREIGN KEY (tenant_id)  REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT tenant_invites_role_fkey      FOREIGN KEY (role_id)    REFERENCES role(role_id)      ON DELETE SET NULL,
    CONSTRAINT tenant_invites_inviter_fkey   FOREIGN KEY (invited_by) REFERENCES users(user_id)     ON DELETE SET NULL
);

CREATE INDEX idx_tenant_invites_tenant ON tenant_invites USING btree (tenant_id);
CREATE INDEX idx_tenant_invites_email  ON tenant_invites USING btree (email);

-- ----------------------------------------------------------------------------
-- Table: email_verification_tokens
-- ----------------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    token_id   UUID    NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID    NOT NULL,
    token_hash VARCHAR NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT email_verification_tokens_pkey           PRIMARY KEY (token_id),
    CONSTRAINT email_verification_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT email_verification_tokens_user_id_fkey   FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_evt_user ON email_verification_tokens USING btree (user_id);

-- ----------------------------------------------------------------------------
-- Table: password_reset_tokens
-- ----------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    token_id   UUID    NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID    NOT NULL,
    token_hash VARCHAR NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT password_reset_tokens_pkey           PRIMARY KEY (token_id),
    CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT password_reset_tokens_user_id_fkey   FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens USING btree (user_id);

-- ----------------------------------------------------------------------------
-- Table: refresh_token
-- tenant_id mirrors the access token's tenant scope.
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_token (
    refresh_token_id BIGSERIAL NOT NULL,
    token_hash       VARCHAR,
    user_id          UUID,
    tenant_id        UUID,
    ip_address       VARCHAR,
    user_agent       VARCHAR,
    expires_at       TIMESTAMP WITHOUT TIME ZONE,
    created_at       TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_revoked       BOOLEAN DEFAULT false,

    CONSTRAINT refresh_token_pkey         PRIMARY KEY (refresh_token_id),
    CONSTRAINT refresh_token_user_id_fkey FOREIGN KEY (user_id)   REFERENCES users(user_id)    ON DELETE CASCADE,
    CONSTRAINT refresh_token_tenant_fkey  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Table: role_permission  (junction)
-- ----------------------------------------------------------------------------
CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    CONSTRAINT role_permission_pkey           PRIMARY KEY (role_id, permission_id),
    CONSTRAINT role_permission_role_id_fkey   FOREIGN KEY (role_id)       REFERENCES role(role_id)             ON DELETE CASCADE,
    CONSTRAINT role_permission_perm_id_fkey   FOREIGN KEY (permission_id) REFERENCES permission(permission_id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Table: user_role  (user ↔ role, scoped to a tenant)
-- tenant_id NULL = system-level assignment (SUPER_ADMIN only).
-- The expression index prevents duplicate (user, role, tenant) combos while
-- allowing the same user to hold the same role in different tenants.
-- ----------------------------------------------------------------------------
CREATE TABLE user_role (
    id        BIGSERIAL NOT NULL,
    role_id   BIGINT    NOT NULL,
    user_id   UUID      NOT NULL,
    tenant_id UUID,

    CONSTRAINT user_role_pkey        PRIMARY KEY (id),
    CONSTRAINT user_role_role_fkey   FOREIGN KEY (role_id)   REFERENCES role(role_id)            ON DELETE CASCADE,
    CONSTRAINT user_role_user_fkey   FOREIGN KEY (user_id)   REFERENCES users(user_id)            ON DELETE CASCADE,
    CONSTRAINT user_role_tenant_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)        ON DELETE CASCADE
);

-- Enforce uniqueness: one role per user per tenant (COALESCE makes NULL comparable).
CREATE UNIQUE INDEX uq_user_role ON user_role
    USING btree (user_id, role_id, COALESCE(tenant_id::text, 'SYSTEM'));

-- ============================================================================
-- SOCIAL CHANNEL MANAGEMENT
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Table: oauth_states  (CSRF state tokens for OAuth flows)
-- Stored server-side so callback can verify the state wasn't tampered with.
-- Expires quickly — OAuth code exchange must happen within 10 minutes.
-- ----------------------------------------------------------------------------
CREATE TABLE oauth_states (
    state_id   UUID    NOT NULL DEFAULT gen_random_uuid(),
    tenant_id  UUID    NOT NULL,
    user_id    UUID    NOT NULL,
    state_hash VARCHAR NOT NULL,    -- SHA-256 of the random state string
    platform   VARCHAR NOT NULL,    -- 'META'
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT oauth_states_pkey            PRIMARY KEY (state_id),
    CONSTRAINT oauth_states_state_hash_key  UNIQUE (state_hash),
    CONSTRAINT oauth_states_tenant_fkey     FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT oauth_states_user_fkey       FOREIGN KEY (user_id)   REFERENCES users(user_id)     ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Table: channels  (connected social media accounts)
-- One row per connected Page or Instagram Business account.
-- access_token_enc is AES-256-GCM encrypted before storage.
-- platform: META_PAGE | INSTAGRAM
-- status  : ACTIVE | EXPIRED | REVOKED | DISCONNECTED
-- ----------------------------------------------------------------------------
CREATE TABLE channels (
    channel_id        UUID    NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID    NOT NULL,
    connected_by      UUID    NOT NULL,    -- user who did the OAuth
    platform          VARCHAR(50)  NOT NULL,
    platform_id       VARCHAR(255) NOT NULL,   -- page_id or ig_user_id from Meta
    name              VARCHAR(255) NOT NULL,   -- human-readable page/account name
    picture_url       TEXT,
    access_token_enc  TEXT    NOT NULL,    -- AES-256-GCM encrypted page token
    token_iv          VARCHAR(64) NOT NULL,  -- base64-encoded GCM IV
    token_tag         VARCHAR(64) NOT NULL,  -- base64-encoded GCM auth tag
    token_expires_at  TIMESTAMP WITHOUT TIME ZONE,   -- NULL = never (page tokens)
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    meta_page_id      VARCHAR(255),    -- populated for INSTAGRAM rows (parent page)
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT channels_pkey                 PRIMARY KEY (channel_id),
    CONSTRAINT channels_tenant_fkey          FOREIGN KEY (tenant_id)    REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT channels_user_fkey            FOREIGN KEY (connected_by) REFERENCES users(user_id)     ON DELETE SET NULL,
    CONSTRAINT channels_unique_per_tenant    UNIQUE (tenant_id, platform, platform_id)
);

CREATE INDEX idx_channels_tenant  ON channels USING btree (tenant_id);
CREATE INDEX idx_channels_status  ON channels USING btree (status);

-- ============================================================================
-- POSTS  (social media posts — draft, scheduled, published)
-- ============================================================================
CREATE TABLE posts (
    post_id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    channel_id          UUID        NOT NULL,
    created_by          UUID        NOT NULL,
    content             TEXT,
    media_urls          TEXT[],                      -- S3/MinIO URLs of attached media
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT|SCHEDULED|PUBLISHING|PUBLISHED|FAILED
    platform_post_id    VARCHAR(255),                -- ID returned by Meta after publish
    scheduled_at        TIMESTAMP WITHOUT TIME ZONE, -- NULL = not scheduled
    published_at        TIMESTAMP WITHOUT TIME ZONE,
    failure_reason      TEXT,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT posts_pkey         PRIMARY KEY (post_id),
    CONSTRAINT posts_tenant_fkey  FOREIGN KEY (tenant_id)  REFERENCES tenants(tenant_id)   ON DELETE CASCADE,
    CONSTRAINT posts_channel_fkey FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    CONSTRAINT posts_user_fkey    FOREIGN KEY (created_by) REFERENCES users(user_id)        ON DELETE SET NULL
);

CREATE INDEX idx_posts_tenant   ON posts USING btree (tenant_id);
CREATE INDEX idx_posts_channel  ON posts USING btree (channel_id);
CREATE INDEX idx_posts_status   ON posts USING btree (status);
CREATE INDEX idx_posts_scheduled ON posts USING btree (scheduled_at) WHERE status = 'SCHEDULED';

-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
