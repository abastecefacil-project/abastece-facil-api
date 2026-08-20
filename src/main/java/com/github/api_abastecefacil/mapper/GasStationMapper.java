package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.gasStation.CreateGasStationRequest;
import com.github.api_abastecefacil.dto.gasStation.GasStationResponse;
import com.github.api_abastecefacil.model.GasStation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class GasStationMapper {

    public GasStation toEntity(CreateGasStationRequest request, BigDecimal latitude, BigDecimal longitude) {
        return new GasStation()
                .setName(request.name())
                .setFantasyName(request.fantasyName())
                .setCnpj(request.cnpj())
                .setCep(request.cep())
                .setDistrict(request.district())
                .setAddress(request.address())
                .setState(request.state())
                .setCity(request.city())
                .setPhone(request.phone())
                .setBusinessHours(request.businessHours())
                .setLatitude(latitude)
                .setLongitude(longitude);
    }

    public GasStationResponse toResponse(GasStation entity) {
        return new GasStationResponse(
                entity.getId(),
                entity.getName(),
                entity.getFantasyName(),
                entity.getCnpj(),
                entity.getCep(),
                entity.getLatitude().toString(),
                entity.getLongitude().toString(),
                entity.getDistrict(),
                entity.getAddress(),
                entity.getState(),
                entity.getCity(),
                entity.getPhone(),
                entity.getBusinessHours(),
                entity.getActive()
        );
    }
}