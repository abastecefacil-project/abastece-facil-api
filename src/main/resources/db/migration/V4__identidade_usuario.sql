-- Convencao de nomenclatura de constraints: ver V2__regionais.sql.
-- Aditiva e idempotente: nenhum DROP, nenhuma alteracao destrutiva.
-- Auto-suficiente: depende apenas de users, que existe nos dois caminhos
-- (V1 no ambiente limpo, init-scripts/dump.sql no ambiente de desenvolvimento).
--
-- password deixa de ser NOT NULL para permitir o cadastro administrativo (S2), em
-- que o usuario nasce sem senha e a define depois pelo fluxo de ativacao (S5).
-- senha_definida com DEFAULT true preserva todos os usuarios ja existentes: quem ja
-- tem senha continua logando exatamente como antes. Usuarios criados pelo fluxo
-- administrativo nascem com false.

ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS telefone character varying(255);

ALTER TABLE users ADD COLUMN IF NOT EXISTS matricula character varying(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS senha_definida boolean NOT NULL DEFAULT true;

-- Indice unico PARCIAL, e nao UNIQUE CONSTRAINT como nas migrations anteriores: o
-- Postgres so aceita predicado WHERE em indice, nunca em constraint. O nome segue a
-- convencao uk_<tabela>_<coluna> mesmo assim, mas o objeto aparece em pg_indexes e
-- NAO em pg_constraint.
--
-- Nota honesta: uma UNIQUE constraint do Postgres ja permite varios NULLs, entao o
-- predicado nao muda o comportamento observavel. Ele torna a intencao explicita e
-- deixa o indice menor.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_matricula
    ON users (matricula)
    WHERE matricula IS NOT NULL;
