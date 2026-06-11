CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS farm;
CREATE SCHEMA IF NOT EXISTS cultivation;
CREATE SCHEMA IF NOT EXISTS traceability;

CREATE TABLE IF NOT EXISTS cultivation.cultivation_schedules (
    id UUID PRIMARY KEY,
    zone_id VARCHAR(64) NOT NULL,
    crop_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scheduled_date DATE NOT NULL,
    scheduled_time TIME NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    dosage VARCHAR(80) NOT NULL,
    assignee VARCHAR(120) NOT NULL,
    safety_interval VARCHAR(80) NOT NULL,
    notes VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cultivation_schedules_status_time
    ON cultivation.cultivation_schedules (status, scheduled_date, scheduled_time);

CREATE INDEX IF NOT EXISTS idx_cultivation_schedules_zone
    ON cultivation.cultivation_schedules (zone_id);

INSERT INTO cultivation.cultivation_schedules (
    id, zone_id, crop_id, type, status, scheduled_date, scheduled_time,
    material_name, dosage, assignee, safety_interval, notes, created_at, updated_at
) VALUES
    (
        '11111111-1111-4111-8111-111111111111',
        'A1',
        'DC-2026-DONA-018',
        'FERTILIZER',
        'PLANNED',
        '2026-06-12',
        '07:30',
        'Phan huu co vi sinh 3-2-2',
        '2.5 kg/cay',
        'To canh tac 01',
        '0 ngay',
        'Rai theo tan, giu cach goc 40 cm, tuoi nhe sau khi rai.',
        NOW(),
        NOW()
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'B2',
        'DC-2026-RI6-012',
        'PESTICIDE',
        'IN_PROGRESS',
        '2026-06-13',
        '16:00',
        'Bacillus subtilis',
        '1.2 lit/ha',
        'KS. Tran Hoang Nam',
        '7 ngay',
        'Phun mat duoi la vao chieu mat, tranh mua trong 6 gio sau phun.',
        NOW(),
        NOW()
    ),
    (
        '33333333-3333-4333-8333-333333333333',
        'A2',
        'DC-2026-DONA-018',
        'INSPECTION',
        'PLANNED',
        '2026-06-14',
        '06:45',
        'Do am dat va ap luc tuoi',
        'Kiem tra 12 tram',
        'Chu vuon Nguyen Minh',
        'Khong ap dung',
        'Uu tien cac cay co do am duoi 72%.',
        NOW(),
        NOW()
    )
ON CONFLICT (id) DO NOTHING;
