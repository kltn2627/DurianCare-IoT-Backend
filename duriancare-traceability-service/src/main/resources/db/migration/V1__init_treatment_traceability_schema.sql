CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE treatment_protocols (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    farm_zone_id UUID NOT NULL,
    crop_season_id UUID NOT NULL,
    disease_record_id UUID,
    created_by_engineer_id UUID NOT NULL,
    approved_by_farmer_id UUID,
    name VARCHAR(200) NOT NULL,
    objective TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    start_date DATE NOT NULL,
    end_date DATE,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_treatment_protocols_status
        CHECK (status IN (
            'DRAFT',
            'PENDING_APPROVAL',
            'APPROVED',
            'ACTIVE',
            'COMPLETED',
            'CANCELLED'
        )),
    CONSTRAINT ck_treatment_protocols_dates
        CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE treatment_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_id UUID NOT NULL,
    day_number INT NOT NULL,
    scheduled_date DATE NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT NOT NULL,
    product_name VARCHAR(200),
    dosage VARCHAR(100),
    safety_interval_days INT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_treatment_steps_protocol
        FOREIGN KEY (protocol_id) REFERENCES treatment_protocols (id) ON DELETE CASCADE,
    CONSTRAINT uk_treatment_steps_protocol_day_order
        UNIQUE (protocol_id, day_number, sort_order),
    CONSTRAINT ck_treatment_steps_day_number CHECK (day_number > 0),
    CONSTRAINT ck_treatment_steps_safety_interval
        CHECK (safety_interval_days IS NULL OR safety_interval_days >= 0),
    CONSTRAINT ck_treatment_steps_action_type
        CHECK (action_type IN (
            'SPRAY',
            'FERTILIZE',
            'WATER',
            'PRUNE',
            'QUARANTINE',
            'INSPECT',
            'OTHER'
        ))
);

CREATE TABLE treatment_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treatment_step_id UUID NOT NULL,
    performed_by_user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    performed_at TIMESTAMPTZ,
    actual_dosage VARCHAR(100),
    evidence_image_url TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_treatment_executions_step
        FOREIGN KEY (treatment_step_id) REFERENCES treatment_steps (id) ON DELETE CASCADE,
    CONSTRAINT ck_treatment_executions_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'SKIPPED', 'FAILED'))
);

CREATE TABLE traceability_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    crop_season_id UUID NOT NULL,
    public_slug VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    last_aggregated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_traceability_profiles_public_slug UNIQUE (public_slug),
    CONSTRAINT uk_traceability_profiles_crop_season UNIQUE (crop_season_id),
    CONSTRAINT ck_traceability_profiles_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE traceability_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    traceability_profile_id UUID NOT NULL,
    version INT NOT NULL,
    farm_snapshot JSONB NOT NULL,
    environment_summary JSONB NOT NULL,
    disease_history JSONB NOT NULL,
    treatment_history JSONB NOT NULL,
    harvest_summary JSONB,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_traceability_snapshots_profile
        FOREIGN KEY (traceability_profile_id)
        REFERENCES traceability_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uk_traceability_snapshots_profile_version
        UNIQUE (traceability_profile_id, version),
    CONSTRAINT ck_traceability_snapshots_version CHECK (version > 0)
);

CREATE TABLE qr_traceability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    traceability_profile_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    public_url TEXT NOT NULL,
    qr_image_url TEXT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qr_traceability_profile
        FOREIGN KEY (traceability_profile_id)
        REFERENCES traceability_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_qr_traceability_snapshot
        FOREIGN KEY (snapshot_id)
        REFERENCES traceability_snapshots (id) ON DELETE RESTRICT,
    CONSTRAINT uk_qr_traceability_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_treatment_protocols_zone_status
    ON treatment_protocols (farm_zone_id, status);
CREATE INDEX idx_treatment_steps_schedule
    ON treatment_steps (scheduled_date, protocol_id);
CREATE INDEX idx_treatment_executions_step_status
    ON treatment_executions (treatment_step_id, status);
CREATE INDEX idx_qr_traceability_profile_active
    ON qr_traceability (traceability_profile_id, active);
