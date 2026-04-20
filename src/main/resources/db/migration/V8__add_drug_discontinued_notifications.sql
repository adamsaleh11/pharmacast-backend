ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'critical_reorder',
        'amber_reorder',
        'daily_digest',
        'weekly_insight',
        'csv_upload_completed',
        'csv_upload_failed',
        'purchase_order_draft',
        'DRUG_DISCONTINUED'
    ));
