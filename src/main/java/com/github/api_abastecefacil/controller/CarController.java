package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.car.CarResponse;
import com.github.api_abastecefacil.dto.car.CreateCarRequest;
import com.github.api_abastecefacil.dto.car.UpdateCarRequest;
import com.github.api_abastecefacil.service.CarService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @PostMapping("/cars")
    public ResponseEntity<CarResponse> createCar(@Valid @RequestBody CreateCarRequest request) {
        CarResponse createdCar = carService.createCar(request);
        URI location = URI.create("/api/cars/" + createdCar.id());
        return ResponseEntity.created(location).body(createdCar);
    }

    @GetMapping("cars/{carId}")
    public ResponseEntity<CarResponse> getCarById(@PathVariable Long carId) {
        return ResponseEntity.ok(carService.getCarById(carId));
    }

    @GetMapping("cars/filter")
    public ResponseEntity<Page<CarResponse>> getCarsByFilters(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(carService.getCarsByFilters(search, active, pageable));
    }

    @PatchMapping("cars/{carId}")
    public ResponseEntity<CarResponse> updateCar(@Valid @PathVariable Long carId, @RequestBody UpdateCarRequest request) {
        return ResponseEntity.ok(carService.updateCar(carId, request));
    }

    @DeleteMapping("cars/{carId}")
    public ResponseEntity<CarResponse> deleteCar(@PathVariable Long carId) {
        carService.deleteCar(carId);
        return ResponseEntity.noContent().build();
    }

}