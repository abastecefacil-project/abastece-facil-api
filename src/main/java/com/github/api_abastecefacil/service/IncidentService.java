package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.incident.CreateIncidentRequest;
import com.github.api_abastecefacil.dto.incident.IncidentDashboardResponse;
import com.github.api_abastecefacil.dto.incident.IncidentResponse;
import com.github.api_abastecefacil.dto.incident.UpdateIncidentRequest;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.IncidentMapper;
import com.github.api_abastecefacil.model.Incident;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Car;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import com.github.api_abastecefacil.repository.NotificacaoRepository;
import com.github.api_abastecefacil.util.PlanilhaXlsx;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static com.github.api_abastecefacil.constants.IncidentConstants.*;

@Service
@Transactional(readOnly = true)
public class IncidentService {

    private static final List<String> CABECALHOS_EXPORTACAO = List.of(
            "Data da ocorrência", "Placa", "Título", "Descrição", "Registrado por", "Registrado em");

    private final CarRepository carRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMapper incidentMapper;
    private final AutorizacaoOperacional autorizacaoOperacional;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final NotificacaoRepository notificacaoRepository;

    public IncidentService(
            CarRepository carRepository,
            IncidentRepository incidentRepository,
            IncidentMapper incidentMapper,
            AutorizacaoOperacional autorizacaoOperacional,
            UsuarioAutenticadoProvider usuarioAutenticadoProvider,
            NotificacaoRepository notificacaoRepository
    ) {
        this.carRepository = carRepository;
        this.incidentRepository = incidentRepository;
        this.incidentMapper = incidentMapper;
        this.autorizacaoOperacional = autorizacaoOperacional;
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.notificacaoRepository = notificacaoRepository;
    }
    
    /**
     * <b>Sem guarda de perfil de propósito.</b> Atende {@code POST /api/public/incident}, que
     * exige JWT, mas vale para qualquer perfil autenticado: registrar ocorrência é o que o
     * colaborador faz. O {@code user_name} gravado é o nome de quem está na sessão, nunca um
     * valor do payload — senão qualquer um registraria ocorrência em nome de outra pessoa.
     * Notifica todos os administradores ativos na mesma transação, como a recusa do Neo.
     */
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Car car = findCarByPlateOrThrow(request.licensePlate());
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        Incident incident = incidentMapper.toEntity(request, autor.getName()).setCar(car);
        Incident savedIncident = incidentRepository.save(incident);
        notificacaoRepository.notificarOcorrencia(savedIncident.getId(), autor.getId());
        return incidentMapper.toResponse(savedIncident);
    }

    public IncidentResponse getIncidentById(Long incidentId) {
        User autor = autorizacaoOperacional.autorizarConsulta();
        Incident incident = findIncidentByIdOrThrow(incidentId, autor);
        return incidentMapper.toResponse(incident);
    }

    public Page<IncidentResponse> getIncidentsByFilters(
            String carPlate,
            String title,
            String userName,
            LocalDate occurrenceDate,
            Pageable pageable
    ) {
        User autor = autorizacaoOperacional.autorizarConsulta();
        Page<Incident> incidentsPage = incidentRepository.findByFilters(
                carPlate,
                title,
                userName,
                occurrenceDate,
                autor.getPerfil() == Perfil.ADMINISTRADOR,
                regionalDoEscopo(autor),
                pageable
        );
        return incidentsPage.map(incidentMapper::toResponse);
    }

    /** Mesmos filtros e escopo da listagem, sem paginação. */
    public byte[] exportarOcorrencias(String carPlate, String title, String userName, LocalDate occurrenceDate) {
        User autor = autorizacaoOperacional.autorizarConsulta();
        List<List<Object>> linhas = incidentRepository
                .findByFilters(carPlate, title, userName, occurrenceDate,
                        autor.getPerfil() == Perfil.ADMINISTRADOR, regionalDoEscopo(autor), Pageable.unpaged())
                .map(incident -> Arrays.<Object>asList(
                        incident.getOccurrenceDate(),
                        incident.getCarPlate(),
                        incident.getTitle(),
                        incident.getDescription(),
                        incident.getUserName(),
                        incident.getCreatedAt()))
                .getContent();
        return PlanilhaXlsx.gerar(CABECALHOS_EXPORTACAO, linhas);
    }

    @Transactional
    public IncidentResponse updateIncident(Long incidentId, UpdateIncidentRequest request) {
        User autor = autorizacaoOperacional.autorizarEscrita();
        Incident incident = findIncidentByIdOrThrow(incidentId, autor);
        updateIncidentDescription(incident, request.description());
        Incident updatedIncident = incidentRepository.save(incident);
        return incidentMapper.toResponse(updatedIncident);
    }

    public IncidentDashboardResponse getIncidentSummary() {
        User autor = autorizacaoOperacional.autorizarConsulta();
        boolean todasRegionais = autor.getPerfil() == Perfil.ADMINISTRADOR;
        Long regionalId = regionalDoEscopo(autor);
        long totalIncidents = todasRegionais
                ? incidentRepository.count()
                : incidentRepository.countByVeiculoRegionalId(regionalId);
        List<IncidentResponse> latestIncidents = fetchLatestIncidents(todasRegionais, regionalId);
        return new IncidentDashboardResponse(totalIncidents, latestIncidents);
    }

    private Incident findIncidentByIdOrThrow(Long incidentId, User autor) {
        return (autor.getPerfil() == Perfil.ADMINISTRADOR
                ? incidentRepository.findById(incidentId)
                : incidentRepository.findByIdAndVeiculoRegionalId(incidentId, regionalDoEscopo(autor)))
                .orElseThrow(() -> new NotFoundException(INCIDENT_NOT_FOUND_MESSAGE));
    }

    private Car findCarByPlateOrThrow(String licensePlate) {
        return carRepository.findByLicensePlate(licensePlate)
                .orElseThrow(() -> new NotFoundException(CAR_NOT_FOUND_BY_PLATE_MESSAGE));
    }

    private void updateIncidentDescription(Incident incident, String description) {
        incident.setDescription(description);
    }

    private List<IncidentResponse> fetchLatestIncidents(boolean todasRegionais, Long regionalId) {
        List<Incident> incidents = todasRegionais
                ? incidentRepository.findAllByOrderByCreatedAtDesc(Limit.of(LATEST_INCIDENTS_LIMIT))
                : regionalId == null ? List.of()
                : incidentRepository.buscarDaRegional(regionalId, createLatestIncidentsPageable()).getContent();
        return incidents
                .stream()
                .map(incidentMapper::toResponse)
                .toList();
    }

    private Long regionalDoEscopo(User autor) {
        return autor.getRegional() == null ? null : autor.getRegional().getId();
    }

    private Pageable createLatestIncidentsPageable() {
        return PageRequest.of(0, LATEST_INCIDENTS_LIMIT);
    }
}
