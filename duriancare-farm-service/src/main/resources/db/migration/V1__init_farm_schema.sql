CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE farms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    address TEXT,
    province VARCHAR(100),
    district VARCHAR(100),
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    area_hectares NUMERIC(12, 2),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_farms_area CHECK (area_hectares IS NULL OR area_hectares > 0),
    CONSTRAINT ck_farms_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_farms_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_farms_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE TABLE farm_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    area_square_meters NUMERIC(14, 2),
    boundary_geojson JSONB,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farm_zones_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
    CONSTRAINT uk_farm_zones_farm_code UNIQUE (farm_id, code),
    CONSTRAINT ck_farm_zones_area
        CHECK (area_square_meters IS NULL OR area_square_meters > 0),
    CONSTRAINT ck_farm_zones_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'QUARANTINED', 'ARCHIVED'))
);

CREATE TABLE species (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    common_name VARCHAR(150) NOT NULL,
    scientific_name VARCHAR(150),
    cultivar VARCHAR(100) NOT NULL,
    expected_yield_kg NUMERIC(12, 2),
    growth_duration_days INT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_species_cultivar UNIQUE (cultivar),
    CONSTRAINT ck_species_expected_yield
        CHECK (expected_yield_kg IS NULL OR expected_yield_kg >= 0),
    CONSTRAINT ck_species_growth_duration
        CHECK (growth_duration_days IS NULL OR growth_duration_days > 0)
);

CREATE TABLE durian_trees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_zone_id UUID NOT NULL,
    species_id UUID NOT NULL,
    tree_code VARCHAR(50) NOT NULL,
    planted_date DATE,
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    health_status VARCHAR(32) NOT NULL DEFAULT 'HEALTHY',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_durian_trees_zone
        FOREIGN KEY (farm_zone_id) REFERENCES farm_zones (id) ON DELETE CASCADE,
    CONSTRAINT fk_durian_trees_species
        FOREIGN KEY (species_id) REFERENCES species (id) ON DELETE RESTRICT,
    CONSTRAINT uk_durian_trees_zone_code UNIQUE (farm_zone_id, tree_code),
    CONSTRAINT ck_durian_trees_health
        CHECK (health_status IN ('HEALTHY', 'SUSPECTED', 'DISEASED', 'TREATING', 'RECOVERED')),
    CONSTRAINT ck_durian_trees_status
        CHECK (status IN ('ACTIVE', 'REMOVED', 'DEAD'))
);

CREATE TABLE farm_authorizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    farm_zone_id UUID,
    owner_user_id UUID NOT NULL,
    engineer_user_id UUID NOT NULL,
    initiated_by_user_id UUID NOT NULL,
    invitation_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farm_authorizations_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
    CONSTRAINT fk_farm_authorizations_zone
        FOREIGN KEY (farm_zone_id) REFERENCES farm_zones (id) ON DELETE CASCADE,
    CONSTRAINT uk_farm_authorizations_scope_engineer
        UNIQUE (farm_id, farm_zone_id, engineer_user_id),
    CONSTRAINT ck_farm_authorizations_users
        CHECK (owner_user_id <> engineer_user_id),
    CONSTRAINT ck_farm_authorizations_invitation_type
        CHECK (invitation_type IN ('OWNER_INVITES_ENGINEER', 'ENGINEER_REQUESTS_ACCESS')),
    CONSTRAINT ck_farm_authorizations_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_farm_authorizations_validity
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from)
);

CREATE TABLE farm_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authorization_id UUID NOT NULL,
    permission VARCHAR(40) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farm_permissions_authorization
        FOREIGN KEY (authorization_id)
        REFERENCES farm_authorizations (id) ON DELETE CASCADE,
    CONSTRAINT uk_farm_permissions_authorization_permission
        UNIQUE (authorization_id, permission),
    CONSTRAINT ck_farm_permissions_permission
        CHECK (permission IN (
            'READ_IOT',
            'READ_DISEASE',
            'CREATE_PROTOCOL',
            'UPDATE_PROTOCOL',
            'CONFIGURE_DEVICE'
        ))
);

CREATE INDEX idx_farms_owner_user_id ON farms (owner_user_id);
CREATE INDEX idx_farm_zones_farm_id ON farm_zones (farm_id);
CREATE INDEX idx_durian_trees_zone_health ON durian_trees (farm_zone_id, health_status);
CREATE INDEX idx_farm_authorizations_engineer_status
    ON farm_authorizations (engineer_user_id, status);
