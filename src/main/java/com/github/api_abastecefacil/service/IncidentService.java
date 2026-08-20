package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.incident.CreateIncidentRequest;
import com.github.api_abastecefacil.dto.incident.IncidentDashboardResponse;
import com.github.api_abastecefacil.dto.incident.IncidentResponse;
import com.github.api_abastecefacil.dto.incident.UpdateIncidentRequest;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.IncidentMapper;
import com.github.api_abastecefacil.model.Incident;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.github.api_abastecefacil.constants.IncidentConstants.*;

@Service
@Transactional(readOnly = true)
public class IncidentService {

    private final CarRepository carRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMapper incidentMapper;

    public IncidentService(
            CarRepository carRepository,
            IncidentRepository incidentRepository,
            IncidentMapper incidentMapper
    ) {
        this.carRepository = carRepository;
        this.incidentRepository = incidentRepository;
        this.incidentMapper = incidentMapper;
    }
    
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        validateCarExistsByPlate(request.licensePlate());
        Incident incident = incidentMapper.toEntity(request);
        Incident savedIncident = incidentRepository.save(incident);
        return incidentMapper.toResponse(savedIncident);
    }

    public IncidentResponse getIncidentById(Long incidentId) {
        Incident incident = findIncidentByIdOrThrow(incidentId);
        return incidentMapper.toResponse(incident);
    }

    public Page<IncidentResponse> getIncidentsByFilters(
            String carPlate,
            String title,
            String userName,
            LocalDate occurrenceDate,
            Pageable pageable
    ) {
        Page<Incident> incidentsPage = incidentRepository.findByFilters(
                carPlate,
                title,
                userName,
                occurrenceDate,
                pageable
        );
        return incidentsPage.map(incidentMapper::toResponse);
    }

    @Transactional
    public IncidentResponse updateIncident(Long incidentId, UpdateIncidentRequest request) {
        Incident incident = findIncidentByIdOrThrow(incidentId);
        updateIncidentDescription(incident, request.description());
        Incident updatedIncident = incidentRepository.save(incident);
        return incidentMapper.toResponse(updatedIncident);
    }

    public IncidentDashboardResponse getIncidentSummary() {
        long totalIncidents = countAllIncidents();
        List<IncidentResponse> latestIncidents = fetchLatestIncidents();
        return new IncidentDashboardResponse(totalIncidents, latestIncidents);
    }

    public Long countAllIncidents() {
        return incidentRepository.count();
    }

    private Incident findIncidentByIdOrThrow(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException(INCIDENT_NOT_FOUND_MESSAGE));
    }

    private void validateCarExistsByPlate(String licensePlate) {
        carRepository.findByLicensePlate(licensePlate)
                .orElseThrow(() -> new NotFoundException(CAR_NOT_FOUND_BY_PLATE_MESSAGE));
    }

    private void updateIncidentDescription(Incident incident, String description) {
        incident.setDescription(description);
    }

    private List<IncidentResponse> fetchLatestIncidents() {
        Pageable latestIncidentsPageable = createLatestIncidentsPageable();
        return incidentRepository.findAll(latestIncidentsPageable)
                .stream()
                .map(incidentMapper::toResponse)
                .toList();
    }

    private Pageable createLatestIncidentsPageable() {
        return PageRequest.of(
                0,
                LATEST_INCIDENTS_LIMIT,
                Sort.by(Sort.Direction.DESC, SORT_BY_FIELD)
        );
    }
}