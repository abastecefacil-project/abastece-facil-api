-- Administradores têm acesso global e não pertencem a uma regional.
UPDATE users SET regional_id = NULL
WHERE perfil = 'ADMINISTRADOR' AND regional_id IS NOT NULL;

ALTER TABLE users ADD CONSTRAINT ck_administrador_sem_regional
    CHECK (perfil <> 'ADMINISTRADOR' OR regional_id IS NULL);
