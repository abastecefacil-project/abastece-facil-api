CREATE TABLE IF NOT EXISTS avaliacoes_postos (
    posto_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    nota smallint NOT NULL,
    comentario varchar(1000),
    atualizado_em timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_avaliacoes_postos PRIMARY KEY (posto_id, usuario_id),
    CONSTRAINT fk_avaliacoes_postos_posto FOREIGN KEY (posto_id) REFERENCES gas_stations(id),
    CONSTRAINT fk_avaliacoes_postos_usuario FOREIGN KEY (usuario_id) REFERENCES users(id),
    CONSTRAINT ck_avaliacoes_postos_nota CHECK (nota BETWEEN 1 AND 5)
);
CREATE INDEX IF NOT EXISTS ix_avaliacoes_postos_recentes ON avaliacoes_postos (posto_id, atualizado_em DESC, usuario_id DESC);
