ALTER TABLE forecasts
    DROP CONSTRAINT IF EXISTS uq_forecasts_location_din;

CREATE INDEX IF NOT EXISTS idx_forecasts_location_generated_at
    ON forecasts(location_id, generated_at DESC);
