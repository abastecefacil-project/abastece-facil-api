package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.car.CarResponse;
import com.github.api_abastecefacil.dto.car.CreateCarRequest;
import com.github.api_abastecefacil.model.Car;
import org.springframework.stereotype.Component;

@Component
public class CarMapper {

    public Car toEntity(CreateCarRequest request) {
        return new Car()
                .setLicensePlate(request.licensePlate())
                .setModel(request.model());
    }

    public CarResponse toResponse(Car car) {
        return new CarResponse(
                car.getId(),
                car.getLicensePlate(),
                car.getModel(),
                car.getActive(),
                car.getCreatedAt(),
                car.getUpdatedAt()
        );
    }
}
