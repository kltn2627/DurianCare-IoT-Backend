CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('FARMER', 'ENGINEER', 'CUSTOMER')),
    CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
);

CREATE TABLE profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20),
    avatar_url TEXT,
    address TEXT,
    province VARCHAR(100),
    agricultural_license_number VARCHAR(100),
    biography TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'vi',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    push_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    disease_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    treatment_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_preferences_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_user_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    connection_type VARCHAR(32) NOT NULL DEFAULT 'PROFESSIONAL',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_connections_requester
        FOREIGN KEY (requester_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_connections_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_connections_pair
        UNIQUE (requester_user_id, recipient_user_id),
    CONSTRAINT ck_user_connections_distinct_users
        CHECK (requester_user_id <> recipient_user_id),
    CONSTRAINT ck_user_connections_type
        CHECK (connection_type IN ('PROFESSIONAL', 'COMMUNITY')),
    CONSTRAINT ck_user_connections_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED'))
);

CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_user_connections_recipient_status
    ON user_connections (recipient_user_id, status);
