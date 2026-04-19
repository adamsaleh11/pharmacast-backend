CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE organizations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    name text NOT NULL,
    stripe_customer_id text,
    subscription_status text,
    trial_ends_at timestamptz
);

CREATE TABLE locations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name text NOT NULL,
    address text NOT NULL,
    deactivated_at timestamptz
);

CREATE TABLE app_users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    email text NOT NULL,
    role text NOT NULL,
    CONSTRAINT ck_app_users_role CHECK (role IN ('owner', 'admin', 'pharmacist', 'staff')),
    CONSTRAINT uq_app_users_email UNIQUE (email)
);

CREATE TABLE drugs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    din text NOT NULL,
    name text NOT NULL,
    strength text NOT NULL,
    form text NOT NULL,
    therapeutic_class text NOT NULL,
    manufacturer text NOT NULL,
    status text NOT NULL,
    last_refreshed_at timestamptz NOT NULL,
    CONSTRAINT uq_drugs_din UNIQUE (din),
    CONSTRAINT ck_drugs_status CHECK (status IN ('active', 'inactive', 'unknown')),
    CONSTRAINT ck_drugs_din_not_blank CHECK (btrim(din) <> '')
);

CREATE TABLE dispensing_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    din text NOT NULL REFERENCES drugs(din) ON DELETE RESTRICT,
    dispensed_date date NOT NULL,
    quantity_dispensed integer NOT NULL,
    quantity_on_hand integer NOT NULL,
    cost_per_unit numeric(12,4),
    patient_id text,
    CONSTRAINT ck_dispensing_quantity_dispensed_non_negative CHECK (quantity_dispensed >= 0),
    CONSTRAINT ck_dispensing_quantity_on_hand_non_negative CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_dispensing_cost_per_unit_non_negative CHECK (cost_per_unit IS NULL OR cost_per_unit >= 0)
);

CREATE TABLE forecasts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    din text NOT NULL REFERENCES drugs(din) ON DELETE RESTRICT,
    generated_at timestamptz NOT NULL,
    forecast_horizon_days integer NOT NULL,
    predicted_quantity integer NOT NULL,
    confidence text NOT NULL,
    days_of_supply numeric(12,1) NOT NULL,
    reorder_status text NOT NULL,
    prophet_lower numeric(12,2) NOT NULL,
    prophet_upper numeric(12,2) NOT NULL,
    avg_daily_demand numeric(12,2),
    reorder_point numeric(12,2),
    data_points_used integer,
    CONSTRAINT ck_forecasts_horizon CHECK (forecast_horizon_days IN (7, 14, 30)),
    CONSTRAINT ck_forecasts_predicted_quantity_non_negative CHECK (predicted_quantity >= 0),
    CONSTRAINT ck_forecasts_confidence CHECK (confidence IN ('low', 'medium', 'high')),
    CONSTRAINT ck_forecasts_reorder_status CHECK (reorder_status IN ('ok', 'amber', 'red')),
    CONSTRAINT ck_forecasts_days_of_supply_non_negative CHECK (days_of_supply >= 0),
    CONSTRAINT ck_forecasts_interval_order CHECK (prophet_lower <= prophet_upper),
    CONSTRAINT ck_forecasts_data_points_minimum CHECK (data_points_used IS NULL OR data_points_used >= 14)
);

CREATE TABLE drug_thresholds (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    din text NOT NULL REFERENCES drugs(din) ON DELETE RESTRICT,
    lead_time_days integer NOT NULL DEFAULT 2,
    red_threshold_days integer NOT NULL DEFAULT 3,
    amber_threshold_days integer NOT NULL DEFAULT 7,
    safety_multiplier text NOT NULL DEFAULT 'balanced',
    notifications_enabled boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_drug_thresholds_location_din UNIQUE (location_id, din),
    CONSTRAINT ck_drug_thresholds_lead_time_positive CHECK (lead_time_days >= 0),
    CONSTRAINT ck_drug_thresholds_red_threshold_positive CHECK (red_threshold_days >= 0),
    CONSTRAINT ck_drug_thresholds_amber_threshold_positive CHECK (amber_threshold_days >= red_threshold_days),
    CONSTRAINT ck_drug_thresholds_safety_multiplier CHECK (safety_multiplier IN ('conservative', 'balanced', 'aggressive'))
);

CREATE TABLE stock_adjustments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    din text NOT NULL REFERENCES drugs(din) ON DELETE RESTRICT,
    adjustment_quantity integer NOT NULL,
    adjusted_at timestamptz NOT NULL,
    note text NOT NULL
);

CREATE TABLE purchase_orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    generated_at timestamptz NOT NULL,
    grok_output text NOT NULL,
    line_items jsonb,
    status text NOT NULL,
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('draft', 'reviewed', 'sent', 'cancelled'))
);

CREATE TABLE notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    location_id uuid REFERENCES locations(id) ON DELETE RESTRICT,
    type text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    sent_at timestamptz,
    read_at timestamptz,
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'critical_reorder',
        'amber_reorder',
        'daily_digest',
        'weekly_insight',
        'csv_upload_completed',
        'csv_upload_failed',
        'purchase_order_draft'
    ))
);

CREATE TABLE csv_uploads (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    filename text NOT NULL,
    status text NOT NULL,
    error_message text,
    row_count integer,
    drug_count integer,
    validation_summary jsonb,
    uploaded_at timestamptz NOT NULL,
    CONSTRAINT ck_csv_uploads_status CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    CONSTRAINT ck_csv_uploads_row_count_non_negative CHECK (row_count IS NULL OR row_count >= 0),
    CONSTRAINT ck_csv_uploads_drug_count_non_negative CHECK (drug_count IS NULL OR drug_count >= 0)
);

CREATE TABLE chat_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    role text NOT NULL,
    content text NOT NULL,
    CONSTRAINT ck_chat_messages_role CHECK (role IN ('user', 'assistant', 'system'))
);

CREATE TABLE notification_settings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    daily_digest_enabled boolean NOT NULL DEFAULT true,
    weekly_insights_enabled boolean NOT NULL DEFAULT true,
    critical_alerts_enabled boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_notification_settings_organization UNIQUE (organization_id)
);

CREATE INDEX idx_locations_organization_id ON locations(organization_id);
CREATE INDEX idx_app_users_organization_id ON app_users(organization_id);
CREATE INDEX idx_dispensing_records_location_din_date ON dispensing_records(location_id, din, dispensed_date);
CREATE INDEX idx_forecasts_location_din_generated_at ON forecasts(location_id, din, generated_at);
CREATE INDEX idx_stock_adjustments_location_din_adjusted_at ON stock_adjustments(location_id, din, adjusted_at);
CREATE INDEX idx_csv_uploads_location_uploaded_at ON csv_uploads(location_id, uploaded_at);
CREATE INDEX idx_notifications_organization_sent_at ON notifications(organization_id, sent_at);
CREATE INDEX idx_drugs_din ON drugs(din);
CREATE INDEX idx_chat_messages_location_created_at ON chat_messages(location_id, created_at);

CREATE TRIGGER trg_organizations_updated_at
BEFORE UPDATE ON organizations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_locations_updated_at
BEFORE UPDATE ON locations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_app_users_updated_at
BEFORE UPDATE ON app_users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_drugs_updated_at
BEFORE UPDATE ON drugs
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_dispensing_records_updated_at
BEFORE UPDATE ON dispensing_records
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_forecasts_updated_at
BEFORE UPDATE ON forecasts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_drug_thresholds_updated_at
BEFORE UPDATE ON drug_thresholds
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_stock_adjustments_updated_at
BEFORE UPDATE ON stock_adjustments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_purchase_orders_updated_at
BEFORE UPDATE ON purchase_orders
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_notifications_updated_at
BEFORE UPDATE ON notifications
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_csv_uploads_updated_at
BEFORE UPDATE ON csv_uploads
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_chat_messages_updated_at
BEFORE UPDATE ON chat_messages
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_notification_settings_updated_at
BEFORE UPDATE ON notification_settings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
