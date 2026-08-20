package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.car.CarResponse;
import com.github.api_abastecefacil.dto.car.CreateCarRequest;
import com.github.api_abastecefacil.dto.car.UpdateCarRequest;
import com.github.api_abastecefacil.exception.CarAlreadyExistsException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.CarMapper;
import com.github.api_abastecefacil.model.Car;
import com.github.api_abastecefacil.repository.CarRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.github.api_abastecefacil.constants.CarConstants.CAR_NOT_FOUND_MESSAGE;
import static com.github.api_abastecefacil.constants.CarConstants.LICENSE_PLATE_ALREADY_EXISTS_MESSAGE;

@Service
@Transactional(readOnly = true)
public class CarService {

    private final CarRepository carRepository;
    private final CarMapper carMapper;

    public CarService(CarRepository carRepository, CarMapper carMapper) {
        this.carRepository = carRepository;
        this.carMapper = carMapper;
    }

    @Transactional
    public CarResponse createCar(CreateCarRequest request) {
        validateLicensePlateDoesNotExist(request.licensePlate());
        Car car = carMapper.toEntity(request);
        Car savedCar = carRepository.save(car);
        return carMapper.toResponse(savedCar);
    }

    public CarResponse getCarById(Long carId) {
        Car car = findCarByIdOrThrow(carId);
        return carMapper.toResponse(car);
    }

    @Transactional
    public CarResponse updateCar(Long carId, UpdateCarRequest request) {
        Car car = findCarByIdOrThrow(carId);
        updateLicensePlateIfProvided(car, carId, request.licensePlate());
        updateModelIfProvided(car, request.model());
        updateActiveStatusIfProvided(car, request.isActive());
        Car updatedCar = carRepository.save(car);
        return carMapper.toResponse(updatedCar);
    }

    @Transactional
    public void deleteCar(Long carId) {
        Car car = findCarByIdOrThrow(carId);
        carRepository.delete(car);
    }

    public Page<CarResponse> getCarsByFilters(String search, Boolean active, Pageable pageable) {
        Page<Car> carsPage = carRepository.findByFilters(search, active, pageable);
        return carsPage.map(carMapper::toResponse);
    }

    private Car findCarByIdOrThrow(Long carId) {
        return carRepository.findById(carId)
                .orElseThrow(() -> new NotFoundException(CAR_NOT_FOUND_MESSAGE));
    }

    private void validateLicensePlateDoesNotExist(String licensePlate) {
        if (carRepository.existsCarByLicensePlate(licensePlate)) {
            throw new CarAlreadyExistsException(LICENSE_PLATE_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void updateLicensePlateIfProvided(Car car, Long currentCarId, String newLicensePlate) {
        if (isBlank(newLicensePlate)) {
            return;
        }
        validateLicensePlateNotUsedByAnotherCar(currentCarId, newLicensePlate);
        car.setLicensePlate(newLicensePlate);
    }

    private void validateLicensePlateNotUsedByAnotherCar(Long currentCarId, String licensePlate) {
        carRepository.findByLicensePlate(licensePlate)
                .filter(existingCar -> !existingCar.getId().equals(currentCarId))
                .ifPresent(existingCar -> {
                    throw new CarAlreadyExistsException(LICENSE_PLATE_ALREADY_EXISTS_MESSAGE);
                });
    }

    private void updateModelIfProvided(Car car, String model) {
        if (isNotBlank(model)) {
            car.setModel(model);
        }
    }

    private void updateActiveStatusIfProvided(Car car, Boolean isActive) {
        if (isActive != null) {
            car.setActive(isActive);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}