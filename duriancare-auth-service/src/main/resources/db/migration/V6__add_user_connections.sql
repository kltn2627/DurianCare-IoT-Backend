CREATE TABLE user_connections (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL,
    receiver_id UUID NOT NULL,
    user_low_id UUID NOT NULL,
    user_high_id UUID NOT NULL,
    requester_role VARCHAR(32) NOT NULL,
    receiver_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    disconnected_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_connections_requester
        FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_connections_receiver
        FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_connections_low_user
        FOREIGN KEY (user_low_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_connections_high_user
        FOREIGN KEY (user_high_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_connections_roles
        CHECK (
            (requester_role = 'FARMER' AND receiver_role = 'ENGINEER')
            OR (requester_role = 'ENGINEER' AND receiver_role = 'FARMER')
        ),
    CONSTRAINT chk_user_connections_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'BLOCKED', 'DISCONNECTED')),
    CONSTRAINT chk_user_connections_source
        CHECK (source IN ('PHONE_SEARCH', 'COMMUNITY')),
    CONSTRAINT chk_user_connections_not_self
        CHECK (requester_id <> receiver_id),
    CONSTRAINT chk_user_connections_ordered_pair
        CHECK (user_low_id < user_high_id)
);

CREATE UNIQUE INDEX ux_user_connections_pair
    ON user_connections (user_low_id, user_high_id);

CREATE INDEX idx_user_connections_requester_status_created_at
    ON user_connections (requester_id, status, created_at DESC);

CREATE INDEX idx_user_connections_receiver_status_created_at
    ON user_connections (receiver_id, status, created_at DESC);

CREATE INDEX idx_user_connections_status_updated_at
    ON user_connections (status, updated_at DESC);

CREATE INDEX idx_user_profiles_phone_normalized
    ON user_profiles (
        lower(replace(replace(replace(replace(replace(coalesce(phone_number, ''), ' ', ''), '-', ''), '.', ''), '(', ''), ')', ''))
    );
