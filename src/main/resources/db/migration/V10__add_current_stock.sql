CREATE TABLE current_stock (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  location_id UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
  din VARCHAR(8) NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(location_id, din)
);

CREATE INDEX idx_current_stock_location_din
  ON current_stock(location_id, din);

ALTER TABLE current_stock ENABLE ROW LEVEL SECURITY;

CREATE POLICY current_stock_location_isolation
  ON current_stock
  USING (
    location_id IN (
      SELECT id FROM locations
      WHERE organization_id = (
        SELECT organization_id FROM app_users
        WHERE id = auth.uid()
      )
    )
  );

ALTER TABLE dispensing_records
  ALTER COLUMN quantity_on_hand DROP NOT NULL;
