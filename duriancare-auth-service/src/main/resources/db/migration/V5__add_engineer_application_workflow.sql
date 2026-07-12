ALTER TABLE users DROP CONSTRAINT chk_users_role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'ENGINEER', 'EXPERT', 'FARMER', 'GUEST'));

CREATE TABLE engineer_applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    workplace VARCHAR(255) NOT NULL,
    specialization VARCHAR(255) NOT NULL,
    years_experience INT NOT NULL,
    biography VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(1000),
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_engineer_applications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_engineer_applications_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_engineer_applications_status
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED'))
);

CREATE TABLE engineer_application_documents (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    document_url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_engineer_application_documents_application
        FOREIGN KEY (application_id) REFERENCES engineer_applications (id) ON DELETE CASCADE
);

CREATE INDEX idx_engineer_applications_status_created_at
    ON engineer_applications (status, created_at DESC);

CREATE INDEX idx_engineer_application_documents_application_id
    ON engineer_application_documents (application_id);
