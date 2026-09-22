-- Dados de acesso exclusivamente para desenvolvimento local.
-- Esta migration e carregada apenas por application.yml, nunca pelo perfil docker.

DELETE FROM users;

INSERT INTO users (
    name,
    email,
    password,
    is_active,
    perfil,
    regional_id,
    matricula,
    senha_definida,
    created_at
) VALUES
(
    'Administrador',
    'admin@abastecefacil.com',
    '$2b$10$EpSs6tFg6GY.trg.SaRQBOn5qJqh4KDMGYv2K4Q35y.B7IseAiFwK',
    true,
    'ADMINISTRADOR',
    NULL,
    NULL,
    true,
    now()
),
(
    'Gestor de Frota',
    'gestor@abastecefacil.com',
    '$2b$10$pa3iKSlRn1akUeJJ9j16/ep9HxafuFjwiauCmGLzDkeaOFNa.781S',
    true,
    'GESTOR_FROTA',
    1,
    'GESTOR001',
    true,
    now()
),
(
    'Colaborador',
    'colaborador@abastecefacil.com',
    '$2b$10$6pXUnzzSXQxaE4SGxzTqruSuVOH20WvnZgc07LgSsZUGhrj7R2gnO',
    true,
    'COLABORADOR',
    NULL,
    NULL,
    true,
    now()
);
