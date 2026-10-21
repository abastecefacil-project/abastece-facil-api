package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.dashboard.DashboardResponse;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                autorizacaoOperacional, clock);
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
}
