package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.CreateGasStationRequest;
import com.github.api_abastecefacil.dto.gasStation.GasStationResponse;
import com.github.api_abastecefacil.dto.gasStation.UpdateGasStationRequest;
import com.github.api_abastecefacil.exception.CoordinatesNotFoundException;
import com.github.api_abastecefacil.exception.GasStationAlreadyExistsException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.mapper.GasStationMapper;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import com.github.api_abastecefacil.service.OpenStreetMapService.Geocodificacao;
import com.github.api_abastecefacil.service.OpenStreetMapService.Origem;
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

import static com.github.api_abastecefacil.constants.GasStationConstants.COORDINATES_NOT_FOUND_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasStationServiceTest {

    @Mock
    private GasStationRepository gasStationRepository;

    @Mock
    private OpenStreetMapService openStreetMapService;

    @Mock
    private GasStationMapper gasStationMapper;

    @Mock
    private AutorizacaoOperacional autorizacaoOperacional;

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
                "11999999999", "08:00 - 22:00", null, null
        );

        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(openStreetMapService.geocodificarComFallback(anyString(), anyString(), anyString()))
                .thenReturn(localizado());
        when(gasStationMapper.toEntity(request, coordinates.latitude(), coordinates.longitude())).thenReturn(gasStation);
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);
        when(gasStationMapper.toResponse(gasStation)).thenReturn(gasStationResponse);

        GasStationResponse response = gasStationService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.cnpj()).isEqualTo("12345678000199");
        verify(gasStationRepository).save(gasStation);
    }

    private Geocodificacao localizado() {
        return new Geocodificacao(Origem.ESTRUTURADA, coordinates, 0);
    }

    /** A mesma estratégia da importação: endereço, cidade e estado — sem bairro e sem CEP. */
    @Test
    void create_ShouldGeocodeWithTheImportStrategy_FromAddressCityAndState() {
        CreateGasStationRequest request = new CreateGasStationRequest(
                "Posto Central", "Central", "12345678000199",
                "01000-000", "Centro", "Rua A, 100", "SP", "São Paulo",
                "11999999999", "08:00 - 22:00", null, null
        );
        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(openStreetMapService.geocodificarComFallback("Rua A, 100", "São Paulo", "SP")).thenReturn(localizado());
        when(gasStationMapper.toEntity(request, coordinates.latitude(), coordinates.longitude())).thenReturn(gasStation);
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);

        gasStationService.create(request);

        verify(openStreetMapService).geocodificarComFallback("Rua A, 100", "São Paulo", "SP");
        verifyNoMoreInteractions(openStreetMapService);
    }

    @Test
    void update_ShouldGeocodeWithTheImportStrategy_AndSaveTheNewCoordinates() {
        UpdateGasStationRequest request = new UpdateGasStationRequest(
                "Posto Central", "Central", "12345678000199", "01000-000", "Centro",
                "Rua B, S/N", "SP", "São Paulo", true, "11999999999", "08:00 - 22:00", null, null
        );
        Coordinates novas = new Coordinates(new BigDecimal("-23.6"), new BigDecimal("-46.7"));
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));
        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(true);
        when(openStreetMapService.geocodificarComFallback("Rua B, S/N", "São Paulo", "SP"))
                .thenReturn(new Geocodificacao(Origem.TEXTO_LIVRE, novas, 1));
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);

        gasStationService.update(1L, request);

        verify(openStreetMapService).geocodificarComFallback("Rua B, S/N", "São Paulo", "SP");
        verifyNoMoreInteractions(openStreetMapService);
        assertThat(gasStation.getLatitude()).isEqualTo(novas.latitude());
        assertThat(gasStation.getLongitude()).isEqualTo(novas.longitude());
    }

    @Test
    void update_ShouldThrowCoordinatesNotFoundException_WithoutSaving_WhenNotFound() {
        UpdateGasStationRequest request = new UpdateGasStationRequest(
                "Posto Central", "Central", "12345678000199", "01000-000", "Centro",
                "Rua B, 10", "SP", "São Paulo", true, "11999999999", "08:00 - 22:00", null, null
        );
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));
        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(true);
        when(openStreetMapService.geocodificarComFallback("Rua B, 10", "São Paulo", "SP"))
                .thenReturn(new Geocodificacao(Origem.NAO_LOCALIZADO, null, 2));

        assertThrows(CoordinatesNotFoundException.class, () -> gasStationService.update(1L, request));
        verify(gasStationRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowGasStationAlreadyExistsException_WhenCnpjExists() {
        CreateGasStationRequest request = new CreateGasStationRequest(
                "Posto Central", "Central", "12345678000199",
                "01000-000", "Centro", "Rua A", "SP", "São Paulo",
                "11999999999", "08:00 - 22:00", null, null
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
                "11999999999", "08:00 - 22:00", null, null
        );

        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(openStreetMapService.geocodificarComFallback(anyString(), anyString(), anyString()))
                .thenReturn(new Geocodificacao(Origem.NAO_LOCALIZADO, null, 0));

        CoordinatesNotFoundException ex =
                assertThrows(CoordinatesNotFoundException.class, () -> gasStationService.create(request));
        assertThat(ex.getMessage()).isEqualTo(
                "Endereço não localizado no mapa. Confira o endereço ou informe a latitude e a longitude manualmente.");
        verify(gasStationRepository, never()).save(any());
    }

    // ------------------------------------------------- coordenadas informadas à mão

    private static CreateGasStationRequest criacaoCom(BigDecimal latitude, BigDecimal longitude) {
        return new CreateGasStationRequest("Posto Central", "Central", "12345678000199", "01000-000", "Centro",
                "Rua A", "SP", "São Paulo", "11999999999", "08:00 - 22:00", latitude, longitude);
    }

    /** Igual ao posto do setUp em tudo, menos no que cada teste passa. */
    private static UpdateGasStationRequest edicao(String cep, String district, String address, String city,
                                                  String state, String phone, BigDecimal latitude,
                                                  BigDecimal longitude) {
        return new UpdateGasStationRequest("Posto Central", "Central", "12345678000199", cep, district, address,
                state, city, true, phone, "08:00 - 22:00", latitude, longitude);
    }

    private static UpdateGasStationRequest edicaoDoEndereco(String address) {
        return edicao("01000-000", "Centro", address, "São Paulo", "SP", null, null, null);
    }

    private void postoExistente() {
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));
        when(gasStationRepository.existsByCnpj("12345678000199")).thenReturn(true);
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);
    }

    private void assertCoordenadasOriginais() {
        assertThat(gasStation.getLatitude()).isEqualTo(coordinates.latitude());
        assertThat(gasStation.getLongitude()).isEqualTo(coordinates.longitude());
    }

    @Test
    void create_ShouldUseInformedCoordinates_WithoutCallingTheGeocodingService() {
        CreateGasStationRequest request = criacaoCom(new BigDecimal("-26.3045"), new BigDecimal("-48.8487"));
        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(gasStationMapper.toEntity(eq(request), any(), any())).thenReturn(gasStation);
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);

        gasStationService.create(request);

        verifyNoInteractions(openStreetMapService);
        verify(gasStationMapper).toEntity(request, new BigDecimal("-26.30450000"), new BigDecimal("-48.84870000"));
    }

    @Test
    void create_ShouldRoundInformedCoordinatesToTheColumnScale() {
        CreateGasStationRequest request = criacaoCom(new BigDecimal("-26.304510993841516"),
                new BigDecimal("-48.848712345678901"));
        when(gasStationRepository.existsByCnpj(request.cnpj())).thenReturn(false);
        when(gasStationMapper.toEntity(eq(request), any(), any())).thenReturn(gasStation);
        when(gasStationRepository.save(gasStation)).thenReturn(gasStation);

        gasStationService.create(request);

        verify(gasStationMapper).toEntity(request, new BigDecimal("-26.30451099"), new BigDecimal("-48.84871235"));
    }

    @Test
    void update_ShouldKeepCoordinatesWithoutGeocoding_WhenOnlyThePhoneChanged() {
        postoExistente();

        gasStationService.update(1L, edicao("01000-000", "Centro", "Rua A", "São Paulo", "SP", "47 3333-0000",
                null, null));

        verifyNoInteractions(openStreetMapService);
        assertCoordenadasOriginais();
        assertThat(gasStation.getPhone()).isEqualTo("47 3333-0000");
        verify(gasStationRepository).save(gasStation);
    }

    @Test
    void update_ShouldNotGeocode_WhenOnlyTheCepChanged() {
        postoExistente();

        gasStationService.update(1L, edicao("01310-100", "Centro", "Rua A", "São Paulo", "SP", null, null, null));

        verifyNoInteractions(openStreetMapService);
        assertCoordenadasOriginais();
        assertThat(gasStation.getCep()).isEqualTo("01310-100");
    }

    @Test
    void update_ShouldNotGeocode_WhenOnlyTheDistrictChanged() {
        postoExistente();

        gasStationService.update(1L, edicao("01000-000", "Bela Vista", "Rua A", "São Paulo", "SP", null, null, null));

        verifyNoInteractions(openStreetMapService);
        assertCoordenadasOriginais();
        assertThat(gasStation.getDistrict()).isEqualTo("Bela Vista");
    }

    @Test
    void update_ShouldNotGeocode_WhenAddressCityAndStateDifferOnlyByCaseAccentOrSpaces() {
        postoExistente();

        gasStationService.update(1L, edicao("01000-000", "Centro", "  RUA   a ", "SAO PAULO", "sp", null, null, null));

        verifyNoInteractions(openStreetMapService);
        assertCoordenadasOriginais();
        // O texto é gravado como veio; só a geocodificação ignora a diferença.
        assertThat(gasStation.getCity()).isEqualTo("SAO PAULO");
    }

    @Test
    void update_ShouldGeocode_WhenTheCityReallyChanged() {
        postoExistente();
        when(openStreetMapService.geocodificarComFallback("Rua A", "Campinas", "SP")).thenReturn(localizado());

        gasStationService.update(1L, edicao("01000-000", "Centro", "Rua A", "Campinas", "SP", null, null, null));

        verify(openStreetMapService).geocodificarComFallback("Rua A", "Campinas", "SP");
    }

    @Test
    void update_ShouldGeocode_WhenTheStateReallyChanged() {
        postoExistente();
        when(openStreetMapService.geocodificarComFallback("Rua A", "São Paulo", "RJ")).thenReturn(localizado());

        gasStationService.update(1L, edicao("01000-000", "Centro", "Rua A", "São Paulo", "RJ", null, null, null));

        verify(openStreetMapService).geocodificarComFallback("Rua A", "São Paulo", "RJ");
    }

    @Test
    void update_ShouldUseInformedCoordinates_EvenWhenTheAddressChanged() {
        postoExistente();

        gasStationService.update(1L, edicao("01000-000", "Centro", "Rua Nova, 500", "São Paulo", "SP", null,
                new BigDecimal("-23.5"), new BigDecimal("-46.6")));

        verifyNoInteractions(openStreetMapService);
        assertThat(gasStation.getLatitude()).isEqualTo(new BigDecimal("-23.50000000"));
        assertThat(gasStation.getLongitude()).isEqualTo(new BigDecimal("-46.60000000"));
        assertThat(gasStation.getAddress()).isEqualTo("Rua Nova, 500");
    }

    @Test
    void update_ShouldLeaveTheStationUntouched_WhenGeocodingFails() {
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));
        when(gasStationRepository.existsByCnpj("12345678000199")).thenReturn(true);
        when(openStreetMapService.geocodificarComFallback("Rua Nova, 500", "São Paulo", "SP"))
                .thenReturn(new Geocodificacao(Origem.NAO_LOCALIZADO, null, 0));

        CoordinatesNotFoundException ex = assertThrows(CoordinatesNotFoundException.class,
                () -> gasStationService.update(1L, edicaoDoEndereco("Rua Nova, 500")));

        assertThat(ex.getMessage()).isEqualTo(COORDINATES_NOT_FOUND_MESSAGE);
        verify(gasStationRepository, never()).save(any());
        assertThat(gasStation.getAddress()).isEqualTo("Rua A");
        assertCoordenadasOriginais();
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

    // ------------------------------------------------- P0.4: autorizacao por perfil

    @Test
    void create_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class, () -> gasStationService.create(null));

        verifyNoInteractions(gasStationRepository, gasStationMapper, openStreetMapService);
    }

    @Test
    void update_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class, () -> gasStationService.update(1L, null));

        verifyNoInteractions(gasStationRepository, gasStationMapper, openStreetMapService);
    }

    @Test
    void deleteGasStation_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class, () -> gasStationService.deleteGasStation(1L));

        verifyNoInteractions(gasStationRepository, gasStationMapper, openStreetMapService);
    }

    @Test
    void findById_ShouldNotAuthorize_BecauseTheRouteIsPublic() {
        // GET /api/public/gas-stations/{id} e publico por contrato: o usuario final
        // consulta posto sem login. Guarda aqui derrubaria a lista e o mapa publicos.
        when(gasStationRepository.findById(1L)).thenReturn(Optional.of(gasStation));
        when(gasStationMapper.toResponse(gasStation)).thenReturn(gasStationResponse);

        gasStationService.findById(1L);

        verifyNoInteractions(autorizacaoOperacional);
    }

    @Test
    void getGasStationsByFilters_ShouldNotAuthorize_BecauseTheRouteIsPublic() {
        Pageable pageable = PageRequest.of(0, 10);
        when(gasStationRepository.findByFilters(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(gasStation)));
        when(gasStationMapper.toResponse(gasStation)).thenReturn(gasStationResponse);

        gasStationService.getGasStationsByFilters(null, null, pageable);

        verifyNoInteractions(autorizacaoOperacional);
    }

}
