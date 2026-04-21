ALTER TABLE forecasts
    ADD COLUMN IF NOT EXISTS model_path text NOT NULL DEFAULT 'unknown';

ALTER TABLE csv_uploads
    ADD COLUMN IF NOT EXISTS backtest_model_version text;

ALTER TABLE csv_uploads
    ADD COLUMN IF NOT EXISTS backtest_model_path_counts jsonb;
