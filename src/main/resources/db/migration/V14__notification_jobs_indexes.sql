CREATE INDEX IF NOT EXISTS idx_notifications_org_location_type_sent_at
    ON notifications (organization_id, location_id, type, sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_payload_din
    ON notifications ((payload ->> 'din'));

CREATE INDEX IF NOT EXISTS idx_locations_active_name
    ON locations (name)
    WHERE deactivated_at IS NULL;
