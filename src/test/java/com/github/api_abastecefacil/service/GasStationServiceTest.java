package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.CreateGasStationRequest;
import com.github.api_abastecefacil.dto.gasStation.GasStationResponse;
import com.github.api_abastecefacil.dto.gasStation.UpdateGasStationRequest;
import com.github.api_abastecefacil.exception.CoordinatesNotFoundException;
import com.github.api_abastecefacil.exception.GasStationAlreadyExistsException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.GasStationMapper;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasStationServiceTest {

    @Mock
    private GasStationRepository gasStationRepository;

    @Mock
    private OpenStreetMapService openStreetMapService;

    @Mock
    private GasStationMapper gasStationMapper;

    @InjectMocks
    private GasStationService gasStationService;

    private GasStation gasStation;
    private GasStationResponse gasStationResponse;
    private Coordinates coordinates;

    @BeforeEach
    void setUp() {
        coordinates = new Coordinates(new BigDecimal("-23.550520"), new BigDecimal("-46.633308"));
        
        gasStation = new GasStation()
                .setId(1L)
                .setName("Posto Central")
                .setCnpj("12345678000199")
                .setAddress("Rua A")
                .setDistrict("Centro")
                .setCity("São Paulo")
                .setState("SP")
                .setCep("01000-000")
                .setLatitude(coordinates.latitude())
                .setLongitude(coordinates.longitude())
                .setActive(true)
                .setCreatedAt(LocalDateTime.now());

        gasStationResponse = new GasStationResponse(
                1L, "Posto Central", "Central", "12345678000199", "01000-000",
                "-23.550520", "-46.633308",
                "Centro", "Rua A", "SP", "São Paulo",
                "11999999999", "08:00 - 22:00", true
        );
    }

    @Test
    void create_ShouldCreateGasStationSuccessfully() {
        CreateGasStationRequest request = new CreateGasStationRequest(
                "Posto Central", "Central", "12345678000199",
                "01000-000", "Centro", "Rua A", "SP", "São Paulo",
                "11999999999", "08:00 - 22:00"
        );

        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(openStreetMapService.getCoordinates(anyString())).thenReturn(Optional.of(coordinates));
        when(gasStationMapper.toEntity(request, coordinates.latitude(), coordinates.longitude())).thenReturn(gasStation);
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);
        when(gasStationMapper.toResponse(gasStation)).thenReturn(gasStationResponse);

        GasStationResponse response = gasStationService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.cnpj()).isEqualTo("12345678000199");
        verify(gasStationRepository).save(gasStation);
    }

    @Test
    void create_ShouldThrowGasStationAlreadyExistsException_WhenCnpjExists() {
        CreateGasStationRequest request = new CreateGasStationRequest(
                "Posto Central", "Central", "12345678000199",
                "01000-000", "Centro", "Rua A", "SP", "São Paulo",
                "11999999999", "08:00 - 22:00"
        );

        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(true);

        assertThrows(GasStationAlreadyExistsException.class, () -> gasStationService.create(request));
        verify(gasStationRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowCoordinatesNotFoundException_WhenCoordinatesNotResolved() {
        CreateGasStationRequest request = new CreateGasStationRequest(
                "Posto Central", "Central", "12345678000199",
                "01000-000", "Centro", "Rua A", "SP", "São Paulo",
                "11999999999", "08:00 - 22:00"
        );

        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(openStreetMapService.getCoordinates(anyString())).thenReturn(Optional.empty());

        assertThrows(CoordinatesNotFoundException.class, () -> gasStationService.create(request));
    }

    @Test
    void findById_ShouldReturnGasStation_WhenIdExists() {
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));
        when(gasStationMapper.toResponse(gasStation)).thenReturn(gasStationResponse);

        GasStationResponse response = gasStationService.findById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void findById_ShouldThrowNotFoundException_WhenIdDoesNotExist() {
        when(gasStationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gasStationService.findById(99L));
    }

    @Test
    void deleteGasStation_ShouldDeleteGasStationSuccessfully() {
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));

        gasStationService.deleteGasStation(1L);

        verify(gasStationRepository).delete(gasStation);
    }

    @Test
    void getGasStationsByFilters_ShouldReturnPagedGasStations() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<GasStation> page = new PageImpl<>(List.of(gasStation));

        when(gasStationRepository.findByFilters("Posto", true, pageable)).thenReturn(page);
        when(gasStationMapper.toResponse(gasStation)).thenReturn(gasStationResponse);

        Page<GasStationResponse> result = gasStationService.getGasStationsByFilters("Posto", true, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }
}
