package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.dashboard.DashboardResponse;
import com.github.api_abastecefacil.dto.dashboard.ResumoOcorrencias;
import com.github.api_abastecefacil.dto.dashboard.ResumoPostos;
import com.github.api_abastecefacil.dto.dashboard.ResumoUsuarios;
import com.github.api_abastecefacil.dto.dashboard.ResumoVeiculos;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import com.github.api_abastecefacil.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.github.api_abastecefacil.constants.DashboardConstants.FUSO_HORARIO;

/**
 * Resumo da Home de gestão. Toda contagem é feita no banco: nenhuma entidade é carregada
 * para ser contada.
 *
 * <p>A autorização é a mesma de {@code GET /api/incidents/dashboard}
 * ({@link AutorizacaoOperacional#autorizarConsulta}), e o autor que ela devolve escopa o
 * bloco de usuários por {@link EscopoUsuarios}, a regra da listagem {@code GET /api/users}.
 * Os demais blocos são globais, porque postos, veículos e ocorrências não têm regional.
 *
 * <p>O {@link Clock} é o bean de {@code SchedulingConfig}, injetado para a virada de mês ser
 * testável. Só o {@code Instant} dele é usado; o fuso vem de {@code DashboardConstants}.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final GasStationRepository gasStationRepository;
    private final CarRepository carRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final AutorizacaoOperacional autorizacaoOperacional;
    private final Clock clock;

    public DashboardService(
            GasStationRepository gasStationRepository,
            CarRepository carRepository,
            IncidentRepository incidentRepository,
            UserRepository userRepository,
            AutorizacaoOperacional autorizacaoOperacional,
            Clock clock
    ) {
        this.gasStationRepository = gasStationRepository;
        this.carRepository = carRepository;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.autorizacaoOperacional = autorizacaoOperacional;
        this.clock = clock;
    }

    public DashboardResponse getResumo() {
        User autor = autorizacaoOperacional.autorizarConsulta();
        return new DashboardResponse(
                resumirPostos(),
                resumirVeiculos(),
                resumirOcorrencias(),
                resumirUsuarios(autor)
        );
    }

    private ResumoPostos resumirPostos() {
        return new ResumoPostos(
                gasStationRepository.countByIsActive(true),
                gasStationRepository.countByIsActive(false),
                gasStationRepository.countCidadesAtendidas()
        );
    }

    private ResumoVeiculos resumirVeiculos() {
        return new ResumoVeiculos(
                carRepository.countByActive(true),
                carRepository.countByActive(false)
        );
    }

    private ResumoOcorrencias resumirOcorrencias() {
        YearMonth mesAtual = YearMonth.from(LocalDate.ofInstant(clock.instant(), FUSO_HORARIO));
        return new ResumoOcorrencias(
                contarOcorrenciasNoMes(mesAtual),
                contarOcorrenciasNoMes(mesAtual.minusMonths(1))
        );
    }

    private long contarOcorrenciasNoMes(YearMonth mes) {
        return incidentRepository.countByOccurrenceDateBetween(mes.atDay(1), mes.atEndOfMonth());
    }

    private ResumoUsuarios resumirUsuarios(User autor) {
        return switch (EscopoUsuarios.de(autor)) {
            case EscopoUsuarios.Todos todos -> new ResumoUsuarios(
                    userRepository.countByIsActiveTrue(),
                    userRepository.countByIsActiveTrueAndSenhaDefinidaFalse());
            case EscopoUsuarios.DaRegional(Long regionalId) -> new ResumoUsuarios(
                    userRepository.countByIsActiveTrueAndRegionalId(regionalId),
                    userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndRegionalId(regionalId));
            case EscopoUsuarios.Proprio(Long userId) -> new ResumoUsuarios(
                    userRepository.countByIsActiveTrueAndId(userId),
                    userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndId(userId));
        };
    }
}
