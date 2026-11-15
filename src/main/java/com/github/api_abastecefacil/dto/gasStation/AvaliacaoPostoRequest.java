package com.github.api_abastecefacil.dto.gasStation;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AvaliacaoPostoRequest(
        @NotNull @Min(1) @Max(5) @Digits(integer = 1, fraction = 0) BigDecimal nota,
        @Size(max = 1000) String comentario
) {}
