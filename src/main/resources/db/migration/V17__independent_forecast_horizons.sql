-- Add is_outdated column to track stale forecasts
ALTER TABLE forecasts
    ADD COLUMN is_outdated BOOLEAN NOT NULL DEFAULT false;

-- Remove duplicate forecasts for the same (location_id, din, forecast_horizon_days) triplet,
-- keeping the newest one (by generated_at)
DELETE FROM forecasts f1
WHERE EXISTS (
    SELECT 1 FROM forecasts f2
    WHERE f1.location_id = f2.location_id
      AND f1.din = f2.din
      AND f1.forecast_horizon_days = f2.forecast_horizon_days
      AND f1.id > f2.id
      AND f1.generated_at <= f2.generated_at
);

-- Create unique constraint on (location_id, din, forecast_horizon_days)
-- This ensures each (location, drug, horizon) tuple has only one forecast
CREATE UNIQUE INDEX IF NOT EXISTS uq_forecasts_location_din_horizon
    ON forecasts(location_id, din, forecast_horizon_days);
