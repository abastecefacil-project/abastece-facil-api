package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.incident.CreateIncidentRequest;
import com.github.api_abastecefacil.dto.incident.IncidentDashboardResponse;
import com.github.api_abastecefacil.dto.incident.IncidentResponse;
import com.github.api_abastecefacil.dto.incident.UpdateIncidentRequest;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.IncidentMapper;
import com.github.api_abastecefacil.model.Car;
import com.github.api_abastecefacil.model.Incident;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private CarRepository carRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentMapper incidentMapper;

    @InjectMocks
    private IncidentService incidentService;

    private Car car;
    private Incident incident;
    private IncidentResponse incidentResponse;

    @BeforeEach
    void setUp() {
        car = new Car().setId(1L).setLicensePlate("ABC1234").setModel("Civic");

        incident = new Incident()
                .setId(1L)
                .setTitle("Pneu Furado")
                .setDescription("Furo no pneu traseiro")
                .setUserName("John Doe")
                .setOccurrenceDate(LocalDate.now());

        incidentResponse = new IncidentResponse(
                1L, "ABC1234", "John Doe", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro", java.time.LocalDateTime.now()
        );
    }

    @Test
    void createIncident_ShouldCreateIncidentSuccessfully() {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "ABC1234", "John Doe", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro"
        );

        when(carRepository.findByLicensePlate("ABC1234")).thenReturn(Optional.of(car));
        when(incidentMapper.toEntity(request)).thenReturn(incident);
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentResponse response = incidentService.createIncident(request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Pneu Furado");
        verify(incidentRepository).save(incident);
    }

    @Test
    void createIncident_ShouldThrowNotFoundException_WhenCarDoesNotExist() {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "UNKNOWN", "John Doe", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro"
        );

        when(carRepository.findByLicensePlate("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> incidentService.createIncident(request));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void getIncidentById_ShouldReturnIncident_WhenIdExists() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentResponse response = incidentService.getIncidentById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getIncidentById_ShouldThrowNotFoundException_WhenIdDoesNotExist() {
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> incidentService.getIncidentById(99L));
    }

    @Test
    void updateIncident_ShouldUpdateDescriptionSuccessfully() {
        UpdateIncidentRequest request = new UpdateIncidentRequest("Nova Descrição");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentResponse response = incidentService.updateIncident(1L, request);

        assertThat(response).isNotNull();
        verify(incidentRepository).save(incident);
    }

    @Test
    void getIncidentSummary_ShouldReturnDashboardResponse() {
        when(incidentRepository.count()).thenReturn(5L);
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentDashboardResponse summary = incidentService.getIncidentSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.total()).isEqualTo(5L);
        assertThat(summary.latestIncidents()).hasSize(1);
    }
}
