package com.github.api_abastecefacil.dto.gasStation;

public record GasStationResponse(
        Long id,
        String name,
        String fantasyName,
        String cnpj,
        String cep,
        String latitude,
        String longitude,
        String district,
        String address,
        String state,
        String city,
        String phone,
        String businessHours,
        boolean isActive
) {

}
