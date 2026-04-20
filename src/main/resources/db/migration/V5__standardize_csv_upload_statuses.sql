ALTER TABLE csv_uploads
    DROP CONSTRAINT IF EXISTS ck_csv_uploads_status;

UPDATE csv_uploads
SET status = CASE status
    WHEN 'pending' THEN 'PENDING'
    WHEN 'processing' THEN 'PROCESSING'
    WHEN 'completed' THEN 'SUCCESS'
    WHEN 'failed' THEN 'ERROR'
    ELSE status
END;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_csv_uploads_status') THEN
        ALTER TABLE csv_uploads
            ADD CONSTRAINT ck_csv_uploads_status
                CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'ERROR'));
    END IF;
END;
$$;
