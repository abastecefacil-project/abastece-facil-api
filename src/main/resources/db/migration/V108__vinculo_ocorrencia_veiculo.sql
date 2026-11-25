ALTER TABLE incidents ADD COLUMN car_id BIGINT REFERENCES cars(id);

UPDATE incidents i
   SET car_id = c.id
  FROM cars c
 WHERE c.license_plate = i.car_plate;

CREATE INDEX idx_incidents_car_id ON incidents(car_id);
