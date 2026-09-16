-- Convencao de nomenclatura de constraints: ver V2__regionais.sql.
-- Aditiva e idempotente: nenhum DROP, nenhuma alteracao destrutiva.
-- Auto-suficiente: nao depende de a V1 ter rodado. Depende apenas de users
-- (criada pela V1 ou pelo init-scripts/dump.sql) e de regionais (V2).
--
-- Usuarios ja existentes ficam COLABORADOR com regional nula: o DEFAULT faz o
-- backfill na mesma instrucao que impoe o NOT NULL. Nenhum dado e perdido.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS perfil character varying(255) NOT NULL DEFAULT 'COLABORADOR';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS regional_id bigint;

-- ADD CONSTRAINT nao aceita IF NOT EXISTS no Postgres 15; guarda explicita.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_users_regional'
          AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_regional
            FOREIGN KEY (regional_id) REFERENCES regionais (id);
    END IF;
END $$;
