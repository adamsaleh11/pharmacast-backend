ALTER TABLE forecasts
    ADD CONSTRAINT uq_forecasts_location_din UNIQUE (location_id, din);
