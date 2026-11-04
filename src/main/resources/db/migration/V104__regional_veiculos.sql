-- Cadastros anteriores ficam sem regional até a classificação pelo administrador.
ALTER TABLE cars ADD COLUMN regional_id BIGINT REFERENCES regionais(id);
CREATE INDEX idx_cars_regional_id ON cars(regional_id);
