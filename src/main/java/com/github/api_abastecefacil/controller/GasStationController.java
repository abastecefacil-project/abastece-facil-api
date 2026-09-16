package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.gasStation.CreateGasStationRequest;
import com.github.api_abastecefacil.dto.gasStation.GasStationResponse;
import com.github.api_abastecefacil.dto.gasStation.UpdateGasStationRequest;
import com.github.api_abastecefacil.service.GasStationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/")
public class GasStationController {

    private final GasStationService gasStationService;

    public GasStationController(GasStationService gasStationService) {
        this.gasStationService = gasStationService;
    }

    @PostMapping("/gas-stations")
    public ResponseEntity<GasStationResponse> create(@Valid @RequestBody CreateGasStationRequest request) {
        GasStationResponse createdGasStation = gasStationService.create(request);
        URI location = URI.create("/api/gas-stations/" + createdGasStation.id());
        return ResponseEntity.created(location).body(createdGasStation);
    }

    @PutMapping("/gas-stations/{id}")
    public ResponseEntity<GasStationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGasStationRequest request) {
        GasStationResponse updatedGasStation = gasStationService.update(id, request);
        return ResponseEntity.ok(updatedGasStation);
    }


    @GetMapping("public/gas-stations/{id}")
    public ResponseEntity<GasStationResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(gasStationService.findById(id));
    }

    @GetMapping("public/gas-stations/filter")
    public ResponseEntity<Page<GasStationResponse>> getGasStationsByFilters(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(gasStationService.getGasStationsByFilters(search, active, pageable));
    }

    @DeleteMapping("/gas-stations/{id}")
    public ResponseEntity<GasStationResponse> deleteGasStation(@PathVariable Long id) {
        gasStationService.deleteGasStation(id);
        return ResponseEntity.noContent().build();
    }

}
