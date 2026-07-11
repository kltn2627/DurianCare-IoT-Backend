CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS kb_reference_sources (
    source_code VARCHAR(80) PRIMARY KEY,
    source_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    publication_title VARCHAR(255),
    publisher VARCHAR(255),
    publication_year INTEGER,
    url TEXT,
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.900,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_kb_reference_sources_type
        CHECK (source_type IN ('OFFICIAL', 'DATABASE', 'PEER_REVIEWED', 'STANDARD', 'EXTENSION'))
);

CREATE TABLE IF NOT EXISTS kb_diseases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    vietnamese_name VARCHAR(200) NOT NULL,
    english_name VARCHAR(200) NOT NULL,
    scientific_name VARCHAR(200),
    issue_type VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    disease_summary TEXT NOT NULL,
    favorable_conditions TEXT,
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.900,
    primary_source_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_diseases_code UNIQUE (code),
    CONSTRAINT ck_kb_diseases_issue_type
        CHECK (issue_type IN ('DISEASE', 'PEST', 'HEALTHY')),
    CONSTRAINT ck_kb_diseases_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT fk_kb_diseases_primary_source
        FOREIGN KEY (primary_source_code) REFERENCES kb_reference_sources (source_code)
            ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS kb_export_markets (
    market_code VARCHAR(10) PRIMARY KEY,
    market_name VARCHAR(100) NOT NULL,
    authority_name VARCHAR(255),
    guidance_url TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_disease_symptoms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disease_code VARCHAR(50) NOT NULL,
    symptom_order INTEGER NOT NULL DEFAULT 1,
    symptom_text TEXT NOT NULL,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.850,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_disease_symptoms_disease
        FOREIGN KEY (disease_code) REFERENCES kb_diseases (code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_disease_symptoms_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_disease_symptoms UNIQUE (disease_code, symptom_order)
);

CREATE TABLE IF NOT EXISTS kb_disease_causes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disease_code VARCHAR(50) NOT NULL,
    cause_order INTEGER NOT NULL DEFAULT 1,
    cause_text TEXT NOT NULL,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.850,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_disease_causes_disease
        FOREIGN KEY (disease_code) REFERENCES kb_diseases (code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_disease_causes_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_disease_causes UNIQUE (disease_code, cause_order)
);

CREATE TABLE IF NOT EXISTS kb_biological_treatments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disease_code VARCHAR(50) NOT NULL,
    treatment_order INTEGER NOT NULL DEFAULT 1,
    treatment_text TEXT NOT NULL,
    mechanism TEXT,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.800,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_biological_treatments_disease
        FOREIGN KEY (disease_code) REFERENCES kb_diseases (code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_biological_treatments_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_biological_treatments UNIQUE (disease_code, treatment_order)
);

CREATE TABLE IF NOT EXISTS kb_organic_treatments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disease_code VARCHAR(50) NOT NULL,
    treatment_order INTEGER NOT NULL DEFAULT 1,
    treatment_text TEXT NOT NULL,
    safe_usage_note TEXT,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.800,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_organic_treatments_disease
        FOREIGN KEY (disease_code) REFERENCES kb_diseases (code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_organic_treatments_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_organic_treatments UNIQUE (disease_code, treatment_order)
);

CREATE TABLE IF NOT EXISTS kb_chemical_treatments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disease_code VARCHAR(50) NOT NULL,
    treatment_order INTEGER NOT NULL DEFAULT 1,
    treatment_text TEXT NOT NULL,
    safe_usage_note TEXT NOT NULL,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.750,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_chemical_treatments_disease
        FOREIGN KEY (disease_code) REFERENCES kb_diseases (code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_chemical_treatments_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_chemical_treatments UNIQUE (disease_code, treatment_order)
);

CREATE TABLE IF NOT EXISTS kb_recommended_chemicals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chemical_treatment_id UUID NOT NULL,
    recommendation_order INTEGER NOT NULL DEFAULT 1,
    product_name VARCHAR(255) NOT NULL,
    active_ingredient_summary TEXT NOT NULL,
    usage_note TEXT,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.750,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_recommended_chemicals_treatment
        FOREIGN KEY (chemical_treatment_id) REFERENCES kb_chemical_treatments (id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_recommended_chemicals_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_recommended_chemicals UNIQUE (chemical_treatment_id, recommendation_order)
);

CREATE TABLE IF NOT EXISTS kb_active_ingredients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_name VARCHAR(255) NOT NULL,
    chemical_group VARCHAR(100),
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.750,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_active_ingredients UNIQUE (ingredient_name),
    CONSTRAINT fk_kb_active_ingredients_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS kb_recommended_chemical_active_ingredients (
    recommended_chemical_id UUID NOT NULL,
    active_ingredient_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recommended_chemical_id, active_ingredient_id),
    CONSTRAINT fk_kb_rcai_recommended_chemical
        FOREIGN KEY (recommended_chemical_id) REFERENCES kb_recommended_chemicals (id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_rcai_active_ingredient
        FOREIGN KEY (active_ingredient_id) REFERENCES kb_active_ingredients (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS kb_harvest_intervals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommended_chemical_id UUID NOT NULL,
    market_code VARCHAR(10) NOT NULL,
    phi_days INTEGER,
    notes TEXT,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_harvest_intervals_chemical
        FOREIGN KEY (recommended_chemical_id) REFERENCES kb_recommended_chemicals (id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_harvest_intervals_market
        FOREIGN KEY (market_code) REFERENCES kb_export_markets (market_code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_harvest_intervals_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_harvest_intervals UNIQUE (recommended_chemical_id, market_code)
);

CREATE TABLE IF NOT EXISTS kb_maximum_residue_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active_ingredient_id UUID NOT NULL,
    market_code VARCHAR(10) NOT NULL,
    commodity_name VARCHAR(255) NOT NULL,
    mrl_value NUMERIC(10,4),
    unit VARCHAR(20) NOT NULL DEFAULT 'mg/kg',
    notes TEXT,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_mrl_active_ingredient
        FOREIGN KEY (active_ingredient_id) REFERENCES kb_active_ingredients (id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_mrl_market
        FOREIGN KEY (market_code) REFERENCES kb_export_markets (market_code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_mrl_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_mrl UNIQUE (active_ingredient_id, market_code, commodity_name)
);

CREATE TABLE IF NOT EXISTS kb_export_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disease_code VARCHAR(50) NOT NULL,
    market_code VARCHAR(10) NOT NULL,
    requirement_order INTEGER NOT NULL DEFAULT 1,
    requirement_text TEXT NOT NULL,
    warning_text TEXT,
    source_code VARCHAR(80),
    confidence_level NUMERIC(4,3) NOT NULL DEFAULT 0.800,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_export_requirements_disease
        FOREIGN KEY (disease_code) REFERENCES kb_diseases (code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_export_requirements_market
        FOREIGN KEY (market_code) REFERENCES kb_export_markets (market_code) ON DELETE CASCADE,
    CONSTRAINT fk_kb_export_requirements_source
        FOREIGN KEY (source_code) REFERENCES kb_reference_sources (source_code) ON DELETE SET NULL,
    CONSTRAINT uk_kb_export_requirements UNIQUE (disease_code, market_code, requirement_order)
);

CREATE INDEX IF NOT EXISTS idx_kb_disease_symptoms_disease_code
    ON kb_disease_symptoms (disease_code, symptom_order);
CREATE INDEX IF NOT EXISTS idx_kb_disease_causes_disease_code
    ON kb_disease_causes (disease_code, cause_order);
CREATE INDEX IF NOT EXISTS idx_kb_biological_treatments_disease_code
    ON kb_biological_treatments (disease_code, treatment_order);
CREATE INDEX IF NOT EXISTS idx_kb_organic_treatments_disease_code
    ON kb_organic_treatments (disease_code, treatment_order);
CREATE INDEX IF NOT EXISTS idx_kb_chemical_treatments_disease_code
    ON kb_chemical_treatments (disease_code, treatment_order);
CREATE INDEX IF NOT EXISTS idx_kb_export_requirements_market_code
    ON kb_export_requirements (market_code, disease_code);

CREATE OR REPLACE FUNCTION kb_touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_kb_reference_sources_updated_at
    BEFORE UPDATE ON kb_reference_sources
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_diseases_updated_at
    BEFORE UPDATE ON kb_diseases
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_export_markets_updated_at
    BEFORE UPDATE ON kb_export_markets
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_disease_symptoms_updated_at
    BEFORE UPDATE ON kb_disease_symptoms
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_disease_causes_updated_at
    BEFORE UPDATE ON kb_disease_causes
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_biological_treatments_updated_at
    BEFORE UPDATE ON kb_biological_treatments
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_organic_treatments_updated_at
    BEFORE UPDATE ON kb_organic_treatments
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_chemical_treatments_updated_at
    BEFORE UPDATE ON kb_chemical_treatments
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_recommended_chemicals_updated_at
    BEFORE UPDATE ON kb_recommended_chemicals
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_active_ingredients_updated_at
    BEFORE UPDATE ON kb_active_ingredients
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_recommended_chemical_active_ingredients_updated_at
    BEFORE UPDATE ON kb_recommended_chemical_active_ingredients
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_harvest_intervals_updated_at
    BEFORE UPDATE ON kb_harvest_intervals
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_maximum_residue_limits_updated_at
    BEFORE UPDATE ON kb_maximum_residue_limits
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();
CREATE TRIGGER trg_kb_export_requirements_updated_at
    BEFORE UPDATE ON kb_export_requirements
    FOR EACH ROW EXECUTE FUNCTION kb_touch_updated_at();

