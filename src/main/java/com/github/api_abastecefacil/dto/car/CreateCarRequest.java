package com.github.api_abastecefacil.dto.car;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCarRequest(
        @NotBlank(message = "Placa do carro é obrigatória")
        @Pattern(
                regexp = "^[A-Z]{3}(?:\\d{4}|\\d[A-Z]\\d{2})$",
                message = "Placa inválida. Use o formato AAA1234 ou AAA1A23"
        )
        String licensePlate,

        @NotBlank(message = "Modelo do carro é obrigatório")
        @Size(min = 3, max = 45, message = "Modelo do carro deve ter entre 3 e 45 caracteres")
        String model
) {
}
