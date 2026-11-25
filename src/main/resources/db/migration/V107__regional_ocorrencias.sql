ALTER TABLE incidents ADD COLUMN regional_id BIGINT REFERENCES regionais(id);

UPDATE incidents i
   SET regional_id = c.regional_id
  FROM cars c
 WHERE c.license_plate = i.car_plate;

CREATE INDEX idx_incidents_regional_id ON incidents(regional_id);
