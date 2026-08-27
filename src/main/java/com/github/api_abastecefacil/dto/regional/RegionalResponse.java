package com.github.api_abastecefacil.dto.regional;

import java.time.LocalDateTime;

public record RegionalResponse(
        Long id,
        String nome,
        String sigla,
        Boolean ativo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
