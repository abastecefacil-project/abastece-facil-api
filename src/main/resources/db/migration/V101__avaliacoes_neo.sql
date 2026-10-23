CREATE TABLE avaliacoes_neo (
    posto_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    passou_neo boolean NOT NULL,
    atualizado_em timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_avaliacoes_neo PRIMARY KEY (posto_id, usuario_id),
    CONSTRAINT fk_avaliacoes_neo_posto FOREIGN KEY (posto_id) REFERENCES gas_stations(id),
    CONSTRAINT fk_avaliacoes_neo_usuario FOREIGN KEY (usuario_id) REFERENCES users(id)
);
