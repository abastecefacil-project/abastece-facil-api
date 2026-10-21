package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.dashboard.CidadeComPostos;
import com.github.api_abastecefacil.dto.dashboard.ContagemAgrupada;
import com.github.api_abastecefacil.dto.dashboard.ContagemCidade;
import com.github.api_abastecefacil.dto.dashboard.DashboardResponse;
import com.github.api_abastecefacil.dto.dashboard.OcorrenciaRecente;
import com.github.api_abastecefacil.dto.dashboard.OcorrenciasNoMes;
import com.github.api_abastecefacil.dto.dashboard.UsuariosNoPerfil;
import com.github.api_abastecefacil.dto.dashboard.VeiculoComOcorrencias;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.mapper.DashboardMapper;
import com.github.api_abastecefacil.model.Incident;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.repository.IncidentRepository;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Clock mockado e construção manual, no padrão do RateLimitServiceTest: a virada de mês e o
 * fuso de São Paulo são provados com instantes escolhidos, sem depender da data em que a
 * suíte roda.
 *
 * <p>Os autores são {@link User} reais, com perfil e regional, para que a regra de
 * {@link EscopoUsuarios} rode de verdade e o teste confira qual contagem foi escolhida.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Instant HOJE = Instant.parse("2026-10-21T15:00:00Z");

    @Mock
    private GasStationRepository gasStationRepository;

    @Mock
    private CarRepository carRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AutorizacaoOperacional autorizacaoOperacional;

    @Mock
    private Clock clock;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                gasStationRepository, carRepository, incidentRepository, userRepository,
                autorizacaoOperacional, new DashboardMapper(), clock);
    }

    private static ContagemAgrupada linha(String chave, long total) {
        return new ContagemAgrupada() {
            @Override
            public String getChave() {
                return chave;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private static ContagemCidade cidade(String nome, String uf, long total) {
        return new ContagemCidade() {
            @Override
            public String getCidade() {
                return nome;
            }

            @Override
            public String getUf() {
                return uf;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private void autenticadoComo(User autor) {
        when(autorizacaoOperacional.autorizarConsulta()).thenReturn(autor);
    }

    private void relogioEm(Instant instante) {
        when(clock.instant()).thenReturn(instante);
    }

    private static User administrador() {
        return new User().setId(1L).setPerfil(Perfil.ADMINISTRADOR);
    }

    private static User gestorDaRegional(Long regionalId) {
        return new User().setId(2L).setPerfil(Perfil.GESTOR_FROTA)
                .setRegional(new Regional().setId(regionalId));
    }

    private static User gestorSemRegional() {
        return new User().setId(3L).setPerfil(Perfil.GESTOR_FROTA);
    }

    // ------------------------------------------------------------------ guarda

    @Test
    void getResumo_ShouldRequireConsultaBeforeTouchingAnything() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarConsulta();

        assertThrows(PerfilNaoPermitidoException.class, () -> dashboardService.getResumo());

        verifyNoInteractions(gasStationRepository, carRepository, incidentRepository, userRepository, clock);
    }

    // ---------------------------------------------------------------- usuarios

    @Test
    void getResumo_ShouldCountAllUsersForAdministrador() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(userRepository.countByIsActiveTrue()).thenReturn(40L);
        when(userRepository.countByIsActiveTrueAndSenhaDefinidaFalse()).thenReturn(6L);

        DashboardResponse resumo = dashboardService.getResumo();

        assertThat(resumo.usuarios().ativos()).isEqualTo(40L);
        assertThat(resumo.usuarios().aguardandoAtivacao()).isEqualTo(6L);
        verify(userRepository, never()).countByIsActiveTrueAndRegionalId(anyLong());
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalseAndRegionalId(anyLong());
        verify(userRepository, never()).countByIsActiveTrueAndId(anyLong());
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalseAndId(anyLong());
    }

    @Test
    void getResumo_ShouldCountOnlyOwnRegionalForGestorWithRegional() {
        autenticadoComo(gestorDaRegional(1L));
        relogioEm(HOJE);
        when(userRepository.countByIsActiveTrueAndRegionalId(1L)).thenReturn(12L);
        when(userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndRegionalId(1L)).thenReturn(2L);

        DashboardResponse resumo = dashboardService.getResumo();

        assertThat(resumo.usuarios().ativos()).isEqualTo(12L);
        assertThat(resumo.usuarios().aguardandoAtivacao()).isEqualTo(2L);
        verify(userRepository, never()).countByIsActiveTrue();
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalse();
        verify(userRepository, never()).countByIsActiveTrueAndId(anyLong());
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalseAndId(anyLong());
    }

    @Test
    void getResumo_ShouldCountOnlySelfForGestorWithoutRegional() {
        autenticadoComo(gestorSemRegional());
        relogioEm(HOJE);
        when(userRepository.countByIsActiveTrueAndId(3L)).thenReturn(1L);
        when(userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndId(3L)).thenReturn(0L);

        DashboardResponse resumo = dashboardService.getResumo();

        assertThat(resumo.usuarios().ativos()).isEqualTo(1L);
        assertThat(resumo.usuarios().aguardandoAtivacao()).isZero();
        verify(userRepository, never()).countByIsActiveTrue();
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalse();
        verify(userRepository, never()).countByIsActiveTrueAndRegionalId(anyLong());
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalseAndRegionalId(anyLong());
    }

    // ------------------------------------------------------- postos e veiculos

    @Test
    void getResumo_ShouldMapStationAndVehicleCounts() {
        autenticadoComo(gestorDaRegional(1L));
        relogioEm(HOJE);
        when(gasStationRepository.countByIsActive(true)).thenReturn(968L);
        when(gasStationRepository.countByIsActive(false)).thenReturn(3L);
        when(gasStationRepository.countCidadesAtendidas()).thenReturn(240L);
        when(carRepository.countByActive(true)).thenReturn(15L);
        when(carRepository.countByActive(false)).thenReturn(4L);

        DashboardResponse resumo = dashboardService.getResumo();

        assertThat(resumo.postos().ativos()).isEqualTo(968L);
        assertThat(resumo.postos().inativos()).isEqualTo(3L);
        assertThat(resumo.postos().cidadesAtendidas()).isEqualTo(240L);
        assertThat(resumo.veiculos().ativos()).isEqualTo(15L);
        assertThat(resumo.veiculos().inativos()).isEqualTo(4L);
    }

    // ------------------------------------------------------------- ocorrencias

    @Test
    void getResumo_ShouldCountIncidentsOfCurrentAndPreviousMonth() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(incidentRepository.countByOccurrenceDateBetween(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(7L);
        when(incidentRepository.countByOccurrenceDateBetween(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(11L);

        DashboardResponse resumo = dashboardService.getResumo();

        assertThat(resumo.ocorrencias().mesAtual()).isEqualTo(7L);
        assertThat(resumo.ocorrencias().mesAnterior()).isEqualTo(11L);
    }

    @Test
    void getResumo_ShouldUseDecemberOfPreviousYearAsPreviousMonthInJanuary() {
        autenticadoComo(administrador());
        relogioEm(Instant.parse("2027-01-15T15:00:00Z"));
        when(incidentRepository.countByOccurrenceDateBetween(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))).thenReturn(2L);
        when(incidentRepository.countByOccurrenceDateBetween(
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))).thenReturn(9L);

        DashboardResponse resumo = dashboardService.getResumo();

        assertThat(resumo.ocorrencias().mesAtual()).isEqualTo(2L);
        assertThat(resumo.ocorrencias().mesAnterior()).isEqualTo(9L);
    }

    @Test
    void getResumo_ShouldResolveCurrentMonthInSaoPauloTimeZone() {
        // 02h UTC de 1º de novembro ainda são 23h de 31 de outubro em São Paulo.
        autenticadoComo(administrador());
        relogioEm(Instant.parse("2026-11-01T02:00:00Z"));

        dashboardService.getResumo();

        verify(incidentRepository).countByOccurrenceDateBetween(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        verify(incidentRepository).countByOccurrenceDateBetween(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        verify(incidentRepository, never()).countByOccurrenceDateBetween(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    }

    // ------------------------------------------------------- serie por mes

    @Test
    void getResumo_ShouldReturnSixMonthSeriesInOrderWithZerosForEmptyMonths() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(incidentRepository.contarPorMes(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(linha("2026-10", 2), linha("2026-07", 3)));

        List<OcorrenciasNoMes> serie = dashboardService.getResumo().ocorrenciasPorMes();

        assertThat(serie).containsExactly(
                new OcorrenciasNoMes("2026-05", 0),
                new OcorrenciasNoMes("2026-06", 0),
                new OcorrenciasNoMes("2026-07", 3),
                new OcorrenciasNoMes("2026-08", 0),
                new OcorrenciasNoMes("2026-09", 0),
                new OcorrenciasNoMes("2026-10", 2));
    }

    @Test
    void getResumo_ShouldReturnSixZerosWhenThereAreNoIncidents() {
        autenticadoComo(administrador());
        relogioEm(HOJE);

        List<OcorrenciasNoMes> serie = dashboardService.getResumo().ocorrenciasPorMes();

        assertThat(serie).hasSize(6).allSatisfy(item -> assertThat(item.total()).isZero());
    }

    @Test
    void getResumo_ShouldBuildSeriesAcrossYearBoundary() {
        autenticadoComo(administrador());
        relogioEm(Instant.parse("2027-02-10T15:00:00Z"));
        when(incidentRepository.contarPorMes(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 2, 28)))
                .thenReturn(List.of(linha("2026-12", 4), linha("2027-01", 1)));

        List<OcorrenciasNoMes> serie = dashboardService.getResumo().ocorrenciasPorMes();

        assertThat(serie).extracting(OcorrenciasNoMes::mes)
                .containsExactly("2026-09", "2026-10", "2026-11", "2026-12", "2027-01", "2027-02");
        assertThat(serie).extracting(OcorrenciasNoMes::total)
                .containsExactly(0L, 0L, 0L, 4L, 1L, 0L);
    }

    // ------------------------------------------------------------- rankings

    @Test
    void getResumo_ShouldRankPlatesInLastNinetyDaysIncludingToday() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(incidentRepository.rankingPlacas(LocalDate.of(2026, 7, 24), LocalDate.of(2026, 10, 21), 5))
                .thenReturn(List.of(linha("MGM5H18", 3), linha("ABC1234", 1)));

        List<VeiculoComOcorrencias> ranking = dashboardService.getResumo().veiculosComMaisOcorrencias();

        assertThat(ranking).containsExactly(
                new VeiculoComOcorrencias("MGM5H18", 3),
                new VeiculoComOcorrencias("ABC1234", 1));
    }

    @Test
    void getResumo_ShouldRankCitiesByActiveStations() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(gasStationRepository.rankingCidades(5))
                .thenReturn(List.of(cidade("JOINVILLE", "SC", 48), cidade("BLUMENAU", "SC", 33)));

        List<CidadeComPostos> ranking = dashboardService.getResumo().cidadesComMaisPostos();

        assertThat(ranking).containsExactly(
                new CidadeComPostos("JOINVILLE", "SC", 48),
                new CidadeComPostos("BLUMENAU", "SC", 33));
    }

    // ------------------------------------------------------ usuarios por perfil

    @Test
    void getResumo_ShouldCountAllProfilesForAdministradorWithZeroForMissing() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(userRepository.contarAtivosPorPerfil())
                .thenReturn(List.of(linha("COLABORADOR", 30), linha("ADMINISTRADOR", 2)));

        List<UsuariosNoPerfil> porPerfil = dashboardService.getResumo().usuariosPorPerfil();

        assertThat(porPerfil).containsExactly(
                new UsuariosNoPerfil(Perfil.COLABORADOR, 30),
                new UsuariosNoPerfil(Perfil.GESTOR_FROTA, 0),
                new UsuariosNoPerfil(Perfil.ADMINISTRADOR, 2));
        verify(userRepository, never()).contarAtivosPorPerfilNaRegional(anyLong());
        verify(userRepository, never()).contarAtivosPorPerfilDoUsuario(anyLong());
    }

    @Test
    void getResumo_ShouldCountProfilesOnlyInOwnRegionalForGestor() {
        autenticadoComo(gestorDaRegional(1L));
        relogioEm(HOJE);
        when(userRepository.contarAtivosPorPerfilNaRegional(1L))
                .thenReturn(List.of(linha("COLABORADOR", 8), linha("GESTOR_FROTA", 2)));

        List<UsuariosNoPerfil> porPerfil = dashboardService.getResumo().usuariosPorPerfil();

        assertThat(porPerfil).containsExactly(
                new UsuariosNoPerfil(Perfil.COLABORADOR, 8),
                new UsuariosNoPerfil(Perfil.GESTOR_FROTA, 2),
                new UsuariosNoPerfil(Perfil.ADMINISTRADOR, 0));
        verify(userRepository, never()).contarAtivosPorPerfil();
        verify(userRepository, never()).contarAtivosPorPerfilDoUsuario(anyLong());
    }

    @Test
    void getResumo_ShouldCountOnlySelfProfileForGestorWithoutRegional() {
        autenticadoComo(gestorSemRegional());
        relogioEm(HOJE);
        when(userRepository.contarAtivosPorPerfilDoUsuario(3L))
                .thenReturn(List.of(linha("GESTOR_FROTA", 1)));

        List<UsuariosNoPerfil> porPerfil = dashboardService.getResumo().usuariosPorPerfil();

        assertThat(porPerfil).extracting(UsuariosNoPerfil::total).containsExactly(0L, 1L, 0L);
        verify(userRepository, never()).contarAtivosPorPerfil();
        verify(userRepository, never()).contarAtivosPorPerfilNaRegional(anyLong());
    }

    // ------------------------------------------------ convites pendentes antigos

    // 15h UTC = 12h em Sao Paulo; o limite e 7 dias antes, na hora de Sao Paulo.
    private static final LocalDateTime LIMITE_CONVITE = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Test
    void getResumo_ShouldCountOldPendingInvitesForAdministrador() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        when(userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBefore(LIMITE_CONVITE))
                .thenReturn(4L);

        assertThat(dashboardService.getResumo().usuariosAguardandoAtivacaoAntigos()).isEqualTo(4L);
        verify(userRepository, never())
                .countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndRegionalId(any(), anyLong());
        verify(userRepository, never())
                .countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndId(any(), anyLong());
    }

    @Test
    void getResumo_ShouldCountOldPendingInvitesOnlyInOwnRegionalForGestor() {
        autenticadoComo(gestorDaRegional(1L));
        relogioEm(HOJE);
        when(userRepository.countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndRegionalId(
                LIMITE_CONVITE, 1L)).thenReturn(2L);

        assertThat(dashboardService.getResumo().usuariosAguardandoAtivacaoAntigos()).isEqualTo(2L);
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBefore(any());
        verify(userRepository, never())
                .countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndId(any(), anyLong());
    }

    @Test
    void getResumo_ShouldCountOldPendingInvitesOnlyForSelfForGestorWithoutRegional() {
        autenticadoComo(gestorSemRegional());
        relogioEm(HOJE);

        assertThat(dashboardService.getResumo().usuariosAguardandoAtivacaoAntigos()).isZero();
        verify(userRepository).countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndId(LIMITE_CONVITE, 3L);
        verify(userRepository, never()).countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBefore(any());
        verify(userRepository, never())
                .countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndRegionalId(any(), anyLong());
    }

    // ---------------------------------------------------- ultimas ocorrencias

    @Test
    void getResumo_ShouldReturnLatestIncidentsWithFourFields() {
        autenticadoComo(administrador());
        relogioEm(HOJE);
        Incident recente = new Incident()
                .setId(9L)
                .setTitle("Sujo internamente")
                .setCarPlate("MGM5H18")
                .setUserName("Fulano")
                .setDescription("texto")
                .setOccurrenceDate(LocalDate.of(2026, 10, 5));
        when(incidentRepository.findAllByOrderByCreatedAtDesc(Limit.of(5))).thenReturn(List.of(recente));

        List<OcorrenciaRecente> ultimas = dashboardService.getResumo().ultimasOcorrencias();

        assertThat(ultimas).containsExactly(
                new OcorrenciaRecente(9L, "Sujo internamente", "MGM5H18", LocalDate.of(2026, 10, 5)));
    }
}
