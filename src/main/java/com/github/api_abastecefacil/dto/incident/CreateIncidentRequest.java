package com.github.api_abastecefacil.dto.incident;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateIncidentRequest(

        @NotNull(message = "A placa do veiculo é obrigatorio")
        String licensePlate,

        @NotNull(message = "A data é obrigatoria")
        LocalDate occurrenceDate,

        @NotNull(message = "O titulo é obrigatorio")
        @NotEmpty(message = "O titulo não pode ser vazio")
        String title,

        String description
) {
}


