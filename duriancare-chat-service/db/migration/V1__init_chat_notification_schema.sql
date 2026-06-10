CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID,
    farm_zone_id UUID,
    conversation_type VARCHAR(30) NOT NULL,
    title VARCHAR(200),
    created_by_user_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_conversations_type
        CHECK (conversation_type IN ('DIRECT', 'FARM_SUPPORT', 'ZONE_SUPPORT'))
);

CREATE TABLE conversation_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMPTZ,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversation_members_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT uk_conversation_members_conversation_user
        UNIQUE (conversation_id, user_id),
    CONSTRAINT ck_conversation_members_role
        CHECK (member_role IN ('OWNER', 'ENGINEER', 'MEMBER', 'BOT')),
    CONSTRAINT ck_conversation_members_dates
        CHECK (left_at IS NULL OR left_at >= joined_at)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    sender_user_id UUID,
    message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    content TEXT NOT NULL,
    metadata JSONB,
    reply_to_message_id UUID,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_reply
        FOREIGN KEY (reply_to_message_id) REFERENCES messages (id) ON DELETE SET NULL,
    CONSTRAINT ck_messages_type
        CHECK (message_type IN (
            'TEXT',
            'IMAGE',
            'FILE',
            'SYSTEM',
            'DISEASE_ALERT',
            'TREATMENT_REMINDER'
        ))
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    reference_type VARCHAR(80),
    reference_id UUID,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_type
        CHECK (notification_type IN (
            'DISEASE_ALERT',
            'TREATMENT_REMINDER',
            'AUTHORIZATION',
            'CHAT'
        )),
    CONSTRAINT ck_notifications_channel
        CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL')),
    CONSTRAINT ck_notifications_status
        CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'))
);

CREATE INDEX idx_conversation_members_user
    ON conversation_members (user_id, conversation_id);
CREATE INDEX idx_messages_conversation_sent
    ON messages (conversation_id, sent_at DESC);
CREATE INDEX idx_notifications_recipient_status
    ON notifications (recipient_user_id, status, created_at DESC);
CREATE INDEX idx_notifications_schedule
    ON notifications (scheduled_at)
    WHERE status = 'PENDING' AND scheduled_at IS NOT NULL;
