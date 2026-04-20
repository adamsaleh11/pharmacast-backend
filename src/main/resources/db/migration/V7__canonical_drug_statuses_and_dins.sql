ALTER TABLE IF EXISTS dispensing_records DROP CONSTRAINT IF EXISTS dispensing_records_din_fkey;
ALTER TABLE IF EXISTS forecasts DROP CONSTRAINT IF EXISTS forecasts_din_fkey;
ALTER TABLE IF EXISTS drug_thresholds DROP CONSTRAINT IF EXISTS drug_thresholds_din_fkey;
ALTER TABLE IF EXISTS stock_adjustments DROP CONSTRAINT IF EXISTS stock_adjustments_din_fkey;

DO $$
BEGIN
    IF to_regclass('public.drugs') IS NOT NULL THEN
        UPDATE drugs
        SET status = CASE lower(status)
            WHEN 'active' THEN 'ACTIVE'
            WHEN 'inactive' THEN 'UNKNOWN'
            WHEN 'unknown' THEN 'UNKNOWN'
            ELSE upper(status)
        END;

        UPDATE drugs
        SET din = lpad(din, 8, '0')
        WHERE din ~ '^[0-9]{1,7}$';
    END IF;

    IF to_regclass('public.dispensing_records') IS NOT NULL THEN
        UPDATE dispensing_records
        SET din = lpad(din, 8, '0')
        WHERE din ~ '^[0-9]{1,7}$';
    END IF;

    IF to_regclass('public.forecasts') IS NOT NULL THEN
        UPDATE forecasts
        SET din = lpad(din, 8, '0')
        WHERE din ~ '^[0-9]{1,7}$';
    END IF;

    IF to_regclass('public.drug_thresholds') IS NOT NULL THEN
        UPDATE drug_thresholds
        SET din = lpad(din, 8, '0')
        WHERE din ~ '^[0-9]{1,7}$';
    END IF;

    IF to_regclass('public.stock_adjustments') IS NOT NULL THEN
        UPDATE stock_adjustments
        SET din = lpad(din, 8, '0')
        WHERE din ~ '^[0-9]{1,7}$';
    END IF;
END $$;

ALTER TABLE IF EXISTS drugs DROP CONSTRAINT IF EXISTS ck_drugs_status;
ALTER TABLE IF EXISTS drugs
    ADD CONSTRAINT ck_drugs_status CHECK (status IN (
        'ACTIVE',
        'APPROVED',
        'MARKETED',
        'DORMANT',
        'CANCELLED',
        'UNVERIFIED',
        'UNKNOWN'
    ));

ALTER TABLE IF EXISTS drugs DROP CONSTRAINT IF EXISTS ck_drugs_din_format;
ALTER TABLE IF EXISTS drugs
    ADD CONSTRAINT ck_drugs_din_format CHECK (din ~ '^[0-9]{8}$');

ALTER TABLE IF EXISTS dispensing_records DROP CONSTRAINT IF EXISTS ck_dispensing_records_din_format;
ALTER TABLE IF EXISTS dispensing_records
    ADD CONSTRAINT ck_dispensing_records_din_format CHECK (din ~ '^[0-9]{8}$');

ALTER TABLE IF EXISTS forecasts DROP CONSTRAINT IF EXISTS ck_forecasts_din_format;
ALTER TABLE IF EXISTS forecasts
    ADD CONSTRAINT ck_forecasts_din_format CHECK (din ~ '^[0-9]{8}$');

ALTER TABLE IF EXISTS drug_thresholds DROP CONSTRAINT IF EXISTS ck_drug_thresholds_din_format;
ALTER TABLE IF EXISTS drug_thresholds
    ADD CONSTRAINT ck_drug_thresholds_din_format CHECK (din ~ '^[0-9]{8}$');

ALTER TABLE IF EXISTS stock_adjustments DROP CONSTRAINT IF EXISTS ck_stock_adjustments_din_format;
ALTER TABLE IF EXISTS stock_adjustments
    ADD CONSTRAINT ck_stock_adjustments_din_format CHECK (din ~ '^[0-9]{8}$');

DO $$
BEGIN
    IF to_regclass('public.drugs') IS NOT NULL AND to_regclass('public.dispensing_records') IS NOT NULL THEN
        ALTER TABLE dispensing_records
            ADD CONSTRAINT dispensing_records_din_fkey FOREIGN KEY (din) REFERENCES drugs(din) ON DELETE RESTRICT;
    END IF;

    IF to_regclass('public.drugs') IS NOT NULL AND to_regclass('public.forecasts') IS NOT NULL THEN
        ALTER TABLE forecasts
            ADD CONSTRAINT forecasts_din_fkey FOREIGN KEY (din) REFERENCES drugs(din) ON DELETE RESTRICT;
    END IF;

    IF to_regclass('public.drugs') IS NOT NULL AND to_regclass('public.drug_thresholds') IS NOT NULL THEN
        ALTER TABLE drug_thresholds
            ADD CONSTRAINT drug_thresholds_din_fkey FOREIGN KEY (din) REFERENCES drugs(din) ON DELETE RESTRICT;
    END IF;

    IF to_regclass('public.drugs') IS NOT NULL AND to_regclass('public.stock_adjustments') IS NOT NULL THEN
        ALTER TABLE stock_adjustments
            ADD CONSTRAINT stock_adjustments_din_fkey FOREIGN KEY (din) REFERENCES drugs(din) ON DELETE RESTRICT;
    END IF;
END $$;
