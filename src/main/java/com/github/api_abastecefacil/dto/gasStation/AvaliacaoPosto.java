package com.github.api_abastecefacil.dto.gasStation;

import java.time.LocalDateTime;

public interface AvaliacaoPosto {
    String getAutor();
    Integer getNota();
    String getComentario();
    LocalDateTime getAtualizadoEm();
}
