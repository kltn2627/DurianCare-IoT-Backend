CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE disease_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    vietnamese_name VARCHAR(150) NOT NULL,
    scientific_name VARCHAR(150),
    description TEXT,
    recommended_action TEXT,
    default_severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_disease_catalog_code UNIQUE (code),
    CONSTRAINT ck_disease_catalog_severity
        CHECK (default_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE disease_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by_user_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    farm_zone_id UUID NOT NULL,
    durian_tree_id UUID,
    disease_code VARCHAR(50) NOT NULL,
    image_url TEXT NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    detected_at TIMESTAMPTZ NOT NULL,
    reviewed_by_engineer_id UUID,
    review_status VARCHAR(20) NOT NULL DEFAULT 'UNREVIEWED',
    engineer_note TEXT,
    bounding_box JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_disease_records_catalog
        FOREIGN KEY (disease_code) REFERENCES disease_catalog (code) ON DELETE RESTRICT,
    CONSTRAINT ck_disease_records_confidence
        CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_disease_records_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_disease_records_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_disease_records_review_status
        CHECK (review_status IN ('UNREVIEWED', 'CONFIRMED', 'CORRECTED', 'REJECTED'))
);

INSERT INTO disease_catalog (
    code,
    vietnamese_name,
    description,
    default_severity
) VALUES
    ('ANTHRACNOSE', 'Bệnh thán thư', 'Vết bệnh nâu đen lan rộng trên lá.', 'HIGH'),
    ('LEAF_BLIGHT', 'Bệnh cháy lá', 'Mép lá cháy khô và lan vào phiến lá.', 'HIGH'),
    ('ALGAL_LEAF_SPOT', 'Bệnh đốm mắt cua', 'Đốm tròn màu nâu hoặc cam trên lá.', 'MEDIUM'),
    ('HEALTHY', 'Lá khỏe mạnh', 'Không phát hiện dấu hiệu bệnh.', 'LOW');

CREATE INDEX idx_disease_records_zone_detected_at
    ON disease_records (farm_zone_id, detected_at DESC);
CREATE INDEX idx_disease_records_tree_detected_at
    ON disease_records (durian_tree_id, detected_at DESC);
CREATE INDEX idx_disease_records_disease_severity
    ON disease_records (disease_code, severity);
