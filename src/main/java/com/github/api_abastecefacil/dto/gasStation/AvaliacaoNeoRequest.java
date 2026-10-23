package com.github.api_abastecefacil.dto.gasStation;

import jakarta.validation.constraints.NotNull;

public record AvaliacaoNeoRequest(
        @NotNull(message = "Informe se conseguiu usar o cartão Neo") Boolean passouNeo
) {}
