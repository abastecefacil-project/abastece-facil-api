package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.incident.CreateIncidentRequest;
import com.github.api_abastecefacil.dto.incident.IncidentDashboardResponse;
import com.github.api_abastecefacil.dto.incident.IncidentResponse;
import com.github.api_abastecefacil.dto.incident.UpdateIncidentRequest;
import com.github.api_abastecefacil.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;


@RestController
@RequestMapping("/api/")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping("/public/incident")
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse createdIncident = incidentService.createIncident(request);
        URI location = URI.create("/api/incidents/" + createdIncident.id());
        return ResponseEntity.created(location).body(createdIncident);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<IncidentResponse> getIncidentById(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncidentById(id));
    }

    @GetMapping("/incidents")
    public ResponseEntity<Page<IncidentResponse>> getIncidentsByFilters(
            @RequestParam(required = false) String carPlate,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<IncidentResponse> response = incidentService.getIncidentsByFilters(carPlate, title, userName, occurrenceDate, pageable);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/incidents/dashboard")
    public ResponseEntity<IncidentDashboardResponse> getIncidentSummary() {
        return ResponseEntity.ok(incidentService.getIncidentSummary());
    }

    @PatchMapping("/incidents/{id}")
    public ResponseEntity<IncidentResponse> updateIncident(@PathVariable Long id, @Valid @RequestBody UpdateIncidentRequest request) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request));
    }

}
