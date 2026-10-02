package com.github.api_abastecefacil.dto.gasStation;

import java.util.UUID;

/** Corpo do 202 de {@code POST /api/gas-stations/import}. O progresso sai em {@code GET .../import/{id}}. */
public record ImportacaoIniciadaResponse(UUID id) {
}
