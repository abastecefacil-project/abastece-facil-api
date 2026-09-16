package com.github.api_abastecefacil.dto.gasStation;

public record GasStationFilterRequest(
        String name,
        String city,
        Boolean active
) {
}
