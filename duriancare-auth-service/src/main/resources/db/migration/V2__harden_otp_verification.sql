ALTER TABLE otp_verifications
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE otp_verifications
    ADD CONSTRAINT chk_otp_failed_attempts
        CHECK (failed_attempts >= 0);
