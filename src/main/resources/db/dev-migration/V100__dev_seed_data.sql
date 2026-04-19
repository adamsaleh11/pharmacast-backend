INSERT INTO organizations (id, name, stripe_customer_id, subscription_status, trial_ends_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Ottawa Independent Pharmacy',
    NULL,
    'trialing',
    now() + interval '14 days'
);

INSERT INTO locations (id, organization_id, name, address)
VALUES (
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000001',
    'Centretown Pharmacy',
    '123 Bank Street, Ottawa, ON'
);

INSERT INTO app_users (id, organization_id, email, role)
VALUES (
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000001',
    'owner@example.pharmaforecast.ca',
    'owner'
);

INSERT INTO notification_settings (
    id,
    organization_id,
    daily_digest_enabled,
    weekly_insights_enabled,
    critical_alerts_enabled
)
VALUES (
    '00000000-0000-0000-0000-000000000301',
    '00000000-0000-0000-0000-000000000001',
    true,
    true,
    true
);

INSERT INTO drugs (
    id,
    din,
    name,
    strength,
    form,
    therapeutic_class,
    manufacturer,
    status,
    last_refreshed_at
)
VALUES
    ('00000000-0000-0000-0000-000000001001', '02242903', 'Metformin', '500 mg', 'Tablet', 'Antidiabetic', 'Sample Pharma', 'active', now()),
    ('00000000-0000-0000-0000-000000001002', '02471477', 'Atorvastatin', '20 mg', 'Tablet', 'Lipid-lowering agent', 'Sample Pharma', 'active', now()),
    ('00000000-0000-0000-0000-000000001003', '02247618', 'Amlodipine', '5 mg', 'Tablet', 'Antihypertensive', 'Sample Pharma', 'active', now()),
    ('00000000-0000-0000-0000-000000001004', '02345678', 'Ramipril', '10 mg', 'Capsule', 'ACE inhibitor', 'Sample Pharma', 'active', now()),
    ('00000000-0000-0000-0000-000000001005', '02012345', 'Levothyroxine', '50 mcg', 'Tablet', 'Thyroid hormone', 'Sample Pharma', 'active', now());

INSERT INTO dispensing_records (
    location_id,
    din,
    dispensed_date,
    quantity_dispensed,
    quantity_on_hand,
    cost_per_unit,
    patient_id
)
VALUES
    ('00000000-0000-0000-0000-000000000101', '02242903', current_date - 21, 90, 420, 0.0425, NULL),
    ('00000000-0000-0000-0000-000000000101', '02242903', current_date - 14, 90, 330, 0.0425, NULL),
    ('00000000-0000-0000-0000-000000000101', '02242903', current_date - 7, 90, 240, 0.0425, NULL),
    ('00000000-0000-0000-0000-000000000101', '02471477', current_date - 20, 30, 180, 0.0810, NULL),
    ('00000000-0000-0000-0000-000000000101', '02471477', current_date - 10, 30, 150, 0.0810, NULL),
    ('00000000-0000-0000-0000-000000000101', '02247618', current_date - 18, 60, 200, 0.0360, NULL),
    ('00000000-0000-0000-0000-000000000101', '02345678', current_date - 12, 30, 120, 0.0550, NULL),
    ('00000000-0000-0000-0000-000000000101', '02012345', current_date - 5, 90, 260, 0.0295, NULL);
