CREATE TABLE knowledge_articles (
    id UUID PRIMARY KEY,
    title VARCHAR(220) NOT NULL,
    slug VARCHAR(260) NOT NULL UNIQUE,
    category VARCHAR(120) NOT NULL,
    author_name VARCHAR(150) NOT NULL,
    author_user_id UUID,
    author_role VARCHAR(20),
    excerpt VARCHAR(600) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    cover_image_url VARCHAR(1000),
    cover_image_key VARCHAR(500),
    cover_image_content_type VARCHAR(120),
    reading_time VARCHAR(40) NOT NULL,
    tags VARCHAR(500),
    published_at TIMESTAMP,
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by UUID,
    rejection_reason VARCHAR(600),
    views BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_knowledge_articles_status
        CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'REJECTED')),
    CONSTRAINT chk_knowledge_articles_author_role
        CHECK (author_role IS NULL OR author_role IN ('ADMIN', 'ENGINEER', 'EXPERT', 'FARMER', 'GUEST')),
    CONSTRAINT fk_knowledge_articles_author
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_articles_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_knowledge_articles_status_updated
    ON knowledge_articles (status, updated_at DESC);

CREATE INDEX idx_knowledge_articles_author
    ON knowledge_articles (author_user_id, updated_at DESC);

