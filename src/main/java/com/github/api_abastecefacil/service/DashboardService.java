package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.dashboard.CidadeComPostos;
import com.github.api_abastecefacil.dto.dashboard.ContagemAgrupada;
import com.github.api_abastecefacil.dto.dashboard.DashboardResponse;
import com.github.api_abastecefacil.dto.dashboard.OcorrenciaRecente;
import com.github.api_abastecefacil.dto.dashboard.OcorrenciasNoMes;
import com.github.api_abastecefacil.dto.dashboard.ResumoOcorrencias;
import com.github.api_abastecefacil.dto.dashboard.ResumoPostos;
import com.github.api_abastecefacil.dto.dashboard.ResumoUsuarios;
import com.github.api_abastecefacil.dto.dashboard.ResumoVeiculos;
import com.github.api_abastecefacil.dto.dashboard.UsuariosNoPerfil;
import com.github.api_abastecefacil.dto.dashboard.VeiculoComOcorrencias;
import com.github.api_abastecefacil.mapper.DashboardMapper;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import com.github.api_abastecefacil.repository.UserRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.api_abastecefacil.constants.DashboardConstants.*;

/**
 * Resumo da Home de gestão. Toda contagem é feita no banco: nenhuma entidade é carregada
 * para ser contada. A única leitura de entidade são as últimas ocorrências, limitadas a
 * {@code LIMITE_ULTIMAS_OCORRENCIAS}.
 *
 * <p>A autorização é a mesma de {@code GET /api/incidents/dashboard}
 * ({@link AutorizacaoOperacional#autorizarConsulta}), e o autor que ela devolve escopa os
 * campos de usuário por {@link EscopoUsuarios}, a regra da listagem {@code GET /api/users}.
 * Os demais blocos são globais, porque postos, veículos e ocorrências não têm regional.
 *
 * <p>O {@link Clock} é o bean de {@code SchedulingConfig}, injetado para as janelas de data
 * serem testáveis. Só o {@code Instant} dele é usado, lido <b>uma vez</b> por resumo, para
 * que todos os blocos vejam o mesmo instante; o fuso vem de {@code DashboardConstants}.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final GasStationRepository gasStationRepository;
    private final CarRepository carRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final AutorizacaoOperacional autorizacaoOperacional;
    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    public DashboardService(
            GasStationRepository gasStationRepository,
            CarRepository carRepository,
            IncidentRepository incidentRepository,
            UserRepository userRepository,
            AutorizacaoOperacional autorizacaoOperacional,
            DashboardMapper dashboardMapper,
            Clock clock
    ) {
        this.gasStationRepository = gasStationRepository;
        this.carRepository = carRepository;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.autorizacaoOperacional = autorizacaoOperacional;
        this.dashboardMapper = dashboardMapper;
        this.clock = clock;
    }

    public DashboardResponse getResumo() {
        User autor = autorizacaoOperacional.autorizarConsulta();

        Instant instante = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(instante, FUSO_HORARIO);
        LocalDateTime agora = LocalDateTime.ofInstant(instante, FUSO_HORARIO);

        return new DashboardResponse(
                resumirPostos(),
                resumirVeiculos(),
                resumirOcorrencias(hoje),
                resumirUsuarios(autor),
                serieOcorrencias(hoje),
                rankingVeiculos(hoje),
                rankingCidades(),
                usuariosPorPerfil(autor),
                convitesPendentesAntigos(autor, agora),
                ultimasOcorrencias()
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

    private ResumoOcorrencias resumirOcorrencias(LocalDate hoje) {
        YearMonth mesAtual = YearMonth.from(hoje);
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

    /**
     * Uma consulta agrupada pelo intervalo inteiro, e os meses sem ocorrência preenchidos aqui
     * com zero: a série tem sempre {@code MESES_SERIE_OCORRENCIAS} itens, em ordem
     * cronológica, terminando no mês atual. {@link YearMonth} resolve a virada de ano.
     */
    private List<OcorrenciasNoMes> serieOcorrencias(LocalDate hoje) {
        YearMonth ultimo = YearMonth.from(hoje);
        YearMonth primeiro = ultimo.minusMonths(MESES_SERIE_OCORRENCIAS - 1);

        Map<String, Long> totalPorMes = paraMapa(
                incidentRepository.contarPorMes(primeiro.atDay(1), ultimo.atEndOfMonth()));

        List<OcorrenciasNoMes> serie = new ArrayList<>(MESES_SERIE_OCORRENCIAS);
        for (YearMonth mes = primeiro; !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            serie.add(new OcorrenciasNoMes(mes.toString(), totalPorMes.getOrDefault(mes.toString(), 0L)));
        }
        return serie;
    }

    /**
     * Janela de {@code DIAS_RANKING_VEICULOS} dias de calendário com hoje incluído. Termina em
     * hoje, então ocorrência com data futura — o cadastro aceita — fica de fora.
     */
    private List<VeiculoComOcorrencias> rankingVeiculos(LocalDate hoje) {
        LocalDate inicio = hoje.minusDays(DIAS_RANKING_VEICULOS - 1);
        return incidentRepository.rankingPlacas(inicio, hoje, LIMITE_RANKING_VEICULOS).stream()
                .map(linha -> new VeiculoComOcorrencias(linha.getChave(), linha.getTotal()))
                .toList();
    }

    private List<CidadeComPostos> rankingCidades() {
        return gasStationRepository.rankingCidades(LIMITE_RANKING_CIDADES).stream()
                .map(linha -> new CidadeComPostos(linha.getCidade(), linha.getUf(), linha.getTotal()))
                .toList();
    }

    /**
     * Os três perfis sempre presentes, na ordem do enum, com zero nos que não têm ninguém no
     * escopo — a Home não precisa adivinhar se um perfil ausente é zero ou erro.
     */
    private List<UsuariosNoPerfil> usuariosPorPerfil(User autor) {
        List<ContagemAgrupada> linhas = switch (EscopoUsuarios.de(autor)) {
            case EscopoUsuarios.Todos todos -> userRepository.contarAtivosPorPerfil();
            case EscopoUsuarios.DaRegional(Long regionalId) ->
                    userRepository.contarAtivosPorPerfilNaRegional(regionalId);
            case EscopoUsuarios.Proprio(Long userId) ->
                    userRepository.contarAtivosPorPerfilDoUsuario(userId);
        };

        Map<String, Long> totalPorPerfil = paraMapa(linhas);
        return Arrays.stream(Perfil.values())
                .map(perfil -> new UsuariosNoPerfil(perfil, totalPorPerfil.getOrDefault(perfil.name(), 0L)))
                .toList();
    }

    private long convitesPendentesAntigos(User autor, LocalDateTime agora) {
        LocalDateTime limite = agora.minusDays(DIAS_CONVITE_PENDENTE_ANTIGO);
        return switch (EscopoUsuarios.de(autor)) {
            case EscopoUsuarios.Todos todos ->
                    userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBefore(limite);
            case EscopoUsuarios.DaRegional(Long regionalId) ->
                    userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndRegionalId(
                            limite, regionalId);
            case EscopoUsuarios.Proprio(Long userId) ->
                    userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndId(
                            limite, userId);
        };
    }

    private List<OcorrenciaRecente> ultimasOcorrencias() {
        return incidentRepository.findAllByOrderByCreatedAtDesc(Limit.of(LIMITE_ULTIMAS_OCORRENCIAS)).stream()
                .map(dashboardMapper::toOcorrenciaRecente)
                .toList();
    }

    private static Map<String, Long> paraMapa(List<ContagemAgrupada> linhas) {
        return linhas.stream()
                .collect(Collectors.toMap(ContagemAgrupada::getChave, ContagemAgrupada::getTotal));
    }
}
