CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_user_id UUID NOT NULL,
    farm_id UUID,
    disease_record_id UUID,
    title VARCHAR(250) NOT NULL,
    content TEXT NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_posts_visibility
        CHECK (visibility IN ('PUBLIC', 'COMMUNITY', 'GROUP')),
    CONSTRAINT ck_posts_status
        CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))
);

CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    parent_comment_id UUID,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_comment_id) REFERENCES comments (id) ON DELETE CASCADE,
    CONSTRAINT ck_comments_status
        CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))
);

CREATE TABLE attachment_info (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    attachment_type VARCHAR(20) NOT NULL,
    url TEXT NOT NULL,
    thumbnail_url TEXT,
    file_name VARCHAR(255),
    mime_type VARCHAR(100),
    file_size_bytes BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attachment_info_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT ck_attachment_info_type
        CHECK (attachment_type IN ('IMAGE', 'VIDEO', 'DOCUMENT')),
    CONSTRAINT ck_attachment_info_file_size
        CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0)
);

CREATE INDEX idx_posts_author_created ON posts (author_user_id, created_at DESC);
CREATE INDEX idx_posts_disease_record ON posts (disease_record_id);
CREATE INDEX idx_comments_post_created ON comments (post_id, created_at);
CREATE INDEX idx_attachment_info_post_order ON attachment_info (post_id, sort_order);
