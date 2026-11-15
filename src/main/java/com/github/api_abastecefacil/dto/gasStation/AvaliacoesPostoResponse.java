package com.github.api_abastecefacil.dto.gasStation;

import org.springframework.data.domain.Page;

public record AvaliacoesPostoResponse(Double media, long total, AvaliacaoPosto minhaAvaliacao,
                                     Page<AvaliacaoPosto> avaliacoes) {}
