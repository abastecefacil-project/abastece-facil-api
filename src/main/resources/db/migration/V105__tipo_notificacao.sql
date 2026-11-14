-- Notificações passam a ter dois tipos: RECUSA_NEO (posto) e OCORRENCIA (veículo).
-- Idempotente, como manda o CLAUDE.md §4 enquanto existir o init-script.
ALTER TABLE notificacoes_neo ADD COLUMN IF NOT EXISTS tipo character varying(255) NOT NULL DEFAULT 'RECUSA_NEO';
ALTER TABLE notificacoes_neo ADD COLUMN IF NOT EXISTS ocorrencia_id bigint;
ALTER TABLE notificacoes_neo ADD COLUMN IF NOT EXISTS ocorrencia_placa character varying(255);
ALTER TABLE notificacoes_neo ADD COLUMN IF NOT EXISTS ocorrencia_titulo character varying(255);
ALTER TABLE notificacoes_neo ALTER COLUMN posto_id DROP NOT NULL;
ALTER TABLE notificacoes_neo ALTER COLUMN posto_nome DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'notificacoes_neo'::regclass AND conname = 'fk_notificacoes_neo_ocorrencia') THEN
        ALTER TABLE notificacoes_neo ADD CONSTRAINT fk_notificacoes_neo_ocorrencia
            FOREIGN KEY (ocorrencia_id) REFERENCES incidents(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'notificacoes_neo'::regclass AND conname = 'ck_notificacoes_neo_tipo') THEN
        ALTER TABLE notificacoes_neo ADD CONSTRAINT ck_notificacoes_neo_tipo CHECK (
            (tipo = 'RECUSA_NEO' AND posto_id IS NOT NULL AND posto_nome IS NOT NULL
                AND ocorrencia_id IS NULL AND ocorrencia_placa IS NULL AND ocorrencia_titulo IS NULL)
            OR (tipo = 'OCORRENCIA' AND ocorrencia_id IS NOT NULL AND ocorrencia_placa IS NOT NULL
                AND ocorrencia_titulo IS NOT NULL AND posto_id IS NULL AND posto_nome IS NULL));
    END IF;
END $$;
