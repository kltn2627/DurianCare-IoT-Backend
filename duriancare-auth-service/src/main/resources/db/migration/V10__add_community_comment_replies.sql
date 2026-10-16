ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS parent_id UUID;

ALTER TABLE community_comments
    DROP CONSTRAINT IF EXISTS fk_community_comments_parent;

ALTER TABLE community_comments
    ADD CONSTRAINT fk_community_comments_parent
        FOREIGN KEY (parent_id) REFERENCES community_comments (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_community_comments_parent
    ON community_comments (parent_id, created_at ASC);
