ALTER TABLE dispensing_records
    DROP CONSTRAINT IF EXISTS dispensing_records_din_fkey;

WITH duplicate_groups AS (
    SELECT
        location_id,
        din,
        dispensed_date,
        (array_agg(id ORDER BY created_at, id))[1] AS keep_id,
        sum(quantity_dispensed) AS quantity_dispensed,
        min(quantity_on_hand) AS quantity_on_hand,
        (array_remove(array_agg(cost_per_unit ORDER BY created_at, id), NULL))[1] AS cost_per_unit
    FROM dispensing_records
    GROUP BY location_id, din, dispensed_date
    HAVING count(*) > 1
),
updated_keepers AS (
    UPDATE dispensing_records dr
    SET
        quantity_dispensed = dg.quantity_dispensed,
        quantity_on_hand = dg.quantity_on_hand,
        cost_per_unit = dg.cost_per_unit,
        patient_id = NULL
    FROM duplicate_groups dg
    WHERE dr.id = dg.keep_id
    RETURNING dr.id
)
DELETE FROM dispensing_records dr
USING duplicate_groups dg
WHERE dr.location_id = dg.location_id
  AND dr.din = dg.din
  AND dr.dispensed_date = dg.dispensed_date
  AND dr.id <> dg.keep_id;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_dispensing_records_location_din_date') THEN
        ALTER TABLE dispensing_records
            ADD CONSTRAINT uq_dispensing_records_location_din_date
                UNIQUE (location_id, din, dispensed_date);
    END IF;
END;
$$;
