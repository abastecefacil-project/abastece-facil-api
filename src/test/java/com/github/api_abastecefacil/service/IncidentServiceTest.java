package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.incident.CreateIncidentRequest;
import com.github.api_abastecefacil.dto.incident.IncidentDashboardResponse;
import com.github.api_abastecefacil.dto.incident.IncidentResponse;
import com.github.api_abastecefacil.dto.incident.UpdateIncidentRequest;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.mapper.IncidentMapper;
import com.github.api_abastecefacil.model.Car;
import com.github.api_abastecefacil.model.Incident;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import com.github.api_abastecefacil.repository.NotificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

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

    @Mock
    private AutorizacaoOperacional autorizacaoOperacional;

    @Mock
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @InjectMocks
    private IncidentService incidentService;

    private Car car;
    private Incident incident;
    private IncidentResponse incidentResponse;

    private void autorizarComoAdministrador() {
        when(autorizacaoOperacional.autorizarConsulta())
                .thenReturn(new User().setPerfil(Perfil.ADMINISTRADOR));
    }

    @BeforeEach
    void setUp() {
        car = new Car().setId(1L).setLicensePlate("ABC1234").setModel("Civic")
                .setRegional(new Regional().setId(1L));

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
                "ABC1234", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro"
        );

        when(carRepository.findByLicensePlate("ABC1234")).thenReturn(Optional.of(car));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(new User().setName("John Doe"));
        when(incidentMapper.toEntity(request, "John Doe")).thenReturn(incident);
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentResponse response = incidentService.createIncident(request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Pneu Furado");
        verify(incidentRepository).save(incident);
        assertThat(incident.getCar()).isSameAs(car);
    }

    @Test
    void createIncident_ShouldThrowNotFoundException_WhenCarDoesNotExist() {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "UNKNOWN", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro"
        );

        when(carRepository.findByLicensePlate("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> incidentService.createIncident(request));
        verify(incidentRepository, never()).save(any());
        verifyNoInteractions(notificacaoRepository);
    }

    @Test
    void createIncident_ShouldNotifyAdministrators_WithSavedIdAndAuthenticatedUser() {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "ABC1234", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro"
        );
        when(carRepository.findByLicensePlate("ABC1234")).thenReturn(Optional.of(car));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(new User().setId(42L).setName("John Doe"));
        when(incidentMapper.toEntity(request, "John Doe")).thenReturn(new Incident());
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.createIncident(request);

        verify(notificacaoRepository).notificarOcorrencia(1L, 42L);
    }

    @Test
    void getIncidentById_ShouldReturnIncident_WhenIdExists() {
        autorizarComoAdministrador();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentResponse response = incidentService.getIncidentById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getIncidentById_ShouldThrowNotFoundException_WhenIdDoesNotExist() {
        autorizarComoAdministrador();
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> incidentService.getIncidentById(99L));
    }

    @Test
    void updateIncident_ShouldUpdateDescriptionSuccessfully() {
        when(autorizacaoOperacional.autorizarEscrita())
                .thenReturn(new User().setPerfil(Perfil.ADMINISTRADOR));
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
        autorizarComoAdministrador();
        when(incidentRepository.count()).thenReturn(5L);
        when(incidentRepository.findAllByOrderByCreatedAtDesc(any(Limit.class))).thenReturn(List.of(incident));
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        IncidentDashboardResponse summary = incidentService.getIncidentSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.total()).isEqualTo(5L);
        assertThat(summary.latestIncidents()).hasSize(1);
    }

    // ------------------------------------------------- P0.4: autorizacao por perfil

    @Test
    void updateIncident_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class,
                () -> incidentService.updateIncident(1L, null));

        verifyNoInteractions(incidentRepository, incidentMapper, carRepository);
    }

    @Test
    void getIncidentById_ShouldRequireConsulta() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarConsulta();

        assertThrows(PerfilNaoPermitidoException.class, () -> incidentService.getIncidentById(1L));

        verifyNoInteractions(incidentRepository, incidentMapper, carRepository);
    }

    @Test
    void getIncidentsByFilters_ShouldRequireConsulta() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarConsulta();

        assertThrows(PerfilNaoPermitidoException.class,
                () -> incidentService.getIncidentsByFilters(
                        null, null, null, null, PageRequest.of(0, 10)));

        verifyNoInteractions(incidentRepository, incidentMapper, carRepository);
    }

    @Test
    void getIncidentSummary_ShouldRequireConsulta() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarConsulta();

        assertThrows(PerfilNaoPermitidoException.class, () -> incidentService.getIncidentSummary());

        verifyNoInteractions(incidentRepository, incidentMapper, carRepository);
    }

    @Test
    void createIncident_ShouldNotAuthorize_BecauseTheRouteIsPublic() {
        // POST /api/public/incident exige JWT, mas vale para qualquer perfil: registrar
        // ocorrencia e exatamente o que o colaborador faz.
        CreateIncidentRequest request = new CreateIncidentRequest(
                "ABC1234", LocalDate.now(), "Pneu Furado", "Furo no pneu traseiro"
        );
        when(carRepository.findByLicensePlate("ABC1234")).thenReturn(Optional.of(car));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(new User().setName("John Doe"));
        when(incidentMapper.toEntity(request, "John Doe")).thenReturn(incident);
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentMapper.toResponse(incident)).thenReturn(incidentResponse);

        incidentService.createIncident(request);

        verifyNoInteractions(autorizacaoOperacional);
    }

    @Test
    void createIncident_ShouldRecordAuthenticatedUserName_IgnoringUserNameFromPayload() throws Exception {
        // Mapper real e ObjectMapper montado como o do Spring Boot (ignora propriedade
        // desconhecida): prova o caminho JSON -> entidade gravada.
        IncidentService service = new IncidentService(
                carRepository, incidentRepository, new IncidentMapper(),
                autorizacaoOperacional, usuarioAutenticadoProvider, notificacaoRepository);
        CreateIncidentRequest request = Jackson2ObjectMapperBuilder.json().build().readValue("""
                {"licensePlate":"ABC1234","userName":"Outra Pessoa","occurrenceDate":"2026-08-18",
                 "title":"Pneu Furado","description":"Furo no pneu traseiro"}
                """, CreateIncidentRequest.class);
        when(carRepository.findByLicensePlate("ABC1234")).thenReturn(Optional.of(car));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(new User().setName("Maria Sessao"));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentResponse response = service.createIncident(request);

        ArgumentCaptor<Incident> salvo = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(salvo.capture());
        assertThat(salvo.getValue().getUserName()).isEqualTo("Maria Sessao");
        assertThat(response.userName()).isEqualTo("Maria Sessao");
        verifyNoInteractions(autorizacaoOperacional);
    }

}
