package com.github.api_abastecefacil.dto.gasStation;

import jakarta.validation.constraints.NotBlank;

public record UpdateGasStationRequest(
        @NotBlank(message = "Nome é obrigatório")
        String name,

        String fantasyName,

        @NotBlank(message = "CNPJ é obrigatório")
        String cnpj,

        @NotBlank(message = "CEP é obrigatório")
        String cep,

        @NotBlank(message = "Bairro é obrigatório")
        String district,

        @NotBlank(message = "Endereço é obrigatório")
        String address,

        @NotBlank(message = "Estado é obrigatório")
        String state,

        @NotBlank(message = "Cidade é obrigatória")
        String city,

        Boolean isActive,

        String phone,

        String businessHours
) {
}
