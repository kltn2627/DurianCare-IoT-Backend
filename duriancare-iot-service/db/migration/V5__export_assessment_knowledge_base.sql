-- V5: Export compliance assessment tables

SET search_path TO duriancare_iot;

-- Chemical registry with PHI and degradation constants
CREATE TABLE IF NOT EXISTS chemical_registry (
  id               VARCHAR(50)    PRIMARY KEY,
  common_name      VARCHAR(100)   NOT NULL,
  vietnamese_name  VARCHAR(100),
  chemical_group   VARCHAR(50),   -- FUNGICIDE | INSECTICIDE | PGR | HERBICIDE
  phi_days         INTEGER        NOT NULL DEFAULT 21,
  half_life_days   DECIMAL(6,2)   NOT NULL DEFAULT 14.0,
  notes            TEXT
);

-- MRL standards per market per chemical (mg/kg = ppm)
CREATE TABLE IF NOT EXISTS market_mrl_standards (
  id          SERIAL PRIMARY KEY,
  market      VARCHAR(20) NOT NULL,  -- CHINA | EU | US | JAPAN | DOMESTIC
  chemical_id VARCHAR(50) NOT NULL REFERENCES chemical_registry(id),
  mrl_ppm     DECIMAL(8,4) NOT NULL,
  banned      BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (market, chemical_id)
);

-- Export assessments log
CREATE TABLE IF NOT EXISTS export_assessments (
  id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id             VARCHAR(100) NOT NULL,
  target_market         VARCHAR(20)  NOT NULL,
  overall_score         DECIMAL(5,2) NOT NULL,
  residue_score         DECIMAL(5,2),
  env_score             DECIMAL(5,2),
  disease_score         DECIMAL(5,2),
  phi_score             DECIMAL(5,2),
  soil_score            DECIMAL(5,2),
  risk_summary          JSONB,
  recommendations       JSONB,
  harvest_date          DATE,
  assessed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_export_assessments_device
  ON export_assessments (device_id, assessed_at DESC);

-- ── Seed: Chemical registry ────────────────────────────────────────────────────

INSERT INTO chemical_registry (id, common_name, vietnamese_name, chemical_group, phi_days, half_life_days, notes) VALUES
  ('paclobutrazol',   'Paclobutrazol',          'Paclobutrazol (kích hoa)',        'PGR',        60,  60.0, 'Commonly used for flower induction in durian'),
  ('hexaconazole',    'Hexaconazole',            'Hexaconazole (trị nấm)',          'FUNGICIDE',  14,  30.0, 'Triazole fungicide for canker/anthracnose'),
  ('metalaxyl',       'Metalaxyl / Mefenoxam',   'Metalaxyl (xì mủ, phytophthora)', 'FUNGICIDE',   7,   7.0, 'Oomycete fungicide for Phytophthora root rot'),
  ('fosetyl_al',      'Fosetyl-Aluminium',       'Fosetyl-Al (thối rễ)',            'FUNGICIDE',   7,  14.0, 'Systemic phosphonate fungicide'),
  ('chlorpyrifos',    'Chlorpyrifos',            'Chlorpyrifos (trừ sâu)',          'INSECTICIDE', 21,  30.0, 'Broad-spectrum insecticide; banned EU'),
  ('thiamethoxam',    'Thiamethoxam',            'Thiamethoxam (rệp, bọ trĩ)',      'INSECTICIDE', 14,  10.0, 'Neonicotinoid systemic insecticide'),
  ('imidacloprid',    'Imidacloprid',            'Imidacloprid (rầy, rệp)',         'INSECTICIDE', 21,  20.0, 'Neonicotinoid; restricted in EU'),
  ('mancozeb',        'Mancozeb',                'Mancozeb (nấm tổng hợp)',         'FUNGICIDE',   7,   3.0, 'Dithiocarbamate; high MRL concern'),
  ('carbendazim',     'Carbendazim (MBC)',        'Carbendazim (thán thư)',          'FUNGICIDE',  14,  20.0, 'Benzimidazole; banned in US'),
  ('dimethoate',      'Dimethoate',              'Dimethoate (ruồi trái)',           'INSECTICIDE', 21,   5.0, 'Organophosphate; strict MRL limits')
ON CONFLICT (id) DO NOTHING;

-- ── Seed: MRL standards (ppm = mg/kg) ─────────────────────────────────────────

INSERT INTO market_mrl_standards (market, chemical_id, mrl_ppm, banned) VALUES
  -- CHINA (GACC / GB 2763)
  ('CHINA', 'paclobutrazol',  0.10,  FALSE),
  ('CHINA', 'hexaconazole',   0.10,  FALSE),
  ('CHINA', 'metalaxyl',      0.50,  FALSE),
  ('CHINA', 'fosetyl_al',    75.00,  FALSE),
  ('CHINA', 'chlorpyrifos',   0.50,  FALSE),
  ('CHINA', 'thiamethoxam',   0.50,  FALSE),
  ('CHINA', 'imidacloprid',   0.50,  FALSE),
  ('CHINA', 'mancozeb',       7.00,  FALSE),
  ('CHINA', 'carbendazim',    0.50,  FALSE),
  ('CHINA', 'dimethoate',     0.20,  FALSE),
  -- EU (EC 396/2005)
  ('EU',    'paclobutrazol',  0.02,  FALSE),
  ('EU',    'hexaconazole',   0.01,  FALSE),
  ('EU',    'metalaxyl',      0.05,  FALSE),
  ('EU',    'fosetyl_al',    75.00,  FALSE),
  ('EU',    'chlorpyrifos',   0.01,  TRUE),
  ('EU',    'thiamethoxam',   0.02,  FALSE),
  ('EU',    'imidacloprid',   0.01,  FALSE),
  ('EU',    'mancozeb',       0.05,  FALSE),
  ('EU',    'carbendazim',    0.10,  FALSE),
  ('EU',    'dimethoate',     0.02,  FALSE),
  -- US (EPA / FDA)
  ('US',    'paclobutrazol',  0.50,  FALSE),
  ('US',    'hexaconazole',   0.10,  FALSE),
  ('US',    'metalaxyl',      0.50,  FALSE),
  ('US',    'fosetyl_al',    75.00,  FALSE),
  ('US',    'chlorpyrifos',   0.10,  FALSE),
  ('US',    'thiamethoxam',   0.50,  FALSE),
  ('US',    'imidacloprid',   0.50,  FALSE),
  ('US',    'mancozeb',       7.00,  FALSE),
  ('US',    'carbendazim',    0.01,  TRUE),
  ('US',    'dimethoate',     0.50,  FALSE),
  -- JAPAN (Food Sanitation Law)
  ('JAPAN', 'paclobutrazol',  0.05,  FALSE),
  ('JAPAN', 'hexaconazole',   0.05,  FALSE),
  ('JAPAN', 'metalaxyl',      0.05,  FALSE),
  ('JAPAN', 'fosetyl_al',    10.00,  FALSE),
  ('JAPAN', 'chlorpyrifos',   0.30,  FALSE),
  ('JAPAN', 'thiamethoxam',   0.30,  FALSE),
  ('JAPAN', 'imidacloprid',   0.20,  FALSE),
  ('JAPAN', 'mancozeb',       2.00,  FALSE),
  ('JAPAN', 'carbendazim',    0.50,  FALSE),
  ('JAPAN', 'dimethoate',     0.10,  FALSE),
  -- DOMESTIC (Vietnam QCVN)
  ('DOMESTIC', 'paclobutrazol', 0.50, FALSE),
  ('DOMESTIC', 'hexaconazole',  0.50, FALSE),
  ('DOMESTIC', 'metalaxyl',     1.00, FALSE),
  ('DOMESTIC', 'fosetyl_al',  100.00, FALSE),
  ('DOMESTIC', 'chlorpyrifos',  0.50, FALSE),
  ('DOMESTIC', 'thiamethoxam',  1.00, FALSE),
  ('DOMESTIC', 'imidacloprid',  1.00, FALSE),
  ('DOMESTIC', 'mancozeb',     10.00, FALSE),
  ('DOMESTIC', 'carbendazim',   1.00, FALSE),
  ('DOMESTIC', 'dimethoate',    0.50, FALSE)
ON CONFLICT (market, chemical_id) DO NOTHING;
