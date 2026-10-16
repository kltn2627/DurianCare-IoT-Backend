CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED')),
    CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'EXPERT', 'FARMER', 'GUEST'))
);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30),
    farm_address VARCHAR(500),
    avatar_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    language VARCHAR(10) NOT NULL DEFAULT 'vi',
    firebase_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE otp_verifications (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    otp_code VARCHAR(100) NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    pending_full_name VARCHAR(150) NOT NULL,
    pending_phone_number VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_otp_verifications_expired_at ON otp_verifications (expired_at);
