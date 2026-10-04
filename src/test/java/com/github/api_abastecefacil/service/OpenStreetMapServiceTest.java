package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.OpenStreetMapClient;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.COUNTRY_CODES_BRASIL;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.DEFAULT_ADDRESS_DETAIL;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.DEFAULT_LIMIT;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.FORMAT_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Geocodificação pelo Nominatim: throttle, restrição ao Brasil e fallback da importação.
 *
 * <p><b>Clock e Espera mockados, sem sleep real.</b> O throttle é provado pelo que ele
 * pede à {@code Espera}, não por tempo decorrido. O relógio só anda quando o teste chama
 * {@code avancarPara(...)}, no padrão do {@code RateLimitServiceTest}.
 *
 * <p>Strictness.LENIENT porque o relógio é stubado no setUp e nem todo teste o consulta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenStreetMapServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final String AGENT = "TestAgent/1.0";
    private static final long INTERVALO_MS = 1100;

    private static final String ADDRESS = "Rua Dona Francisca, 1285";
    private static final String DISTRICT = "Centro";
    private static final String CITY = "Joinville";
    private static final String STATE = "SC";
    private static final String CEP = "89201-250";
    private static final String CONSULTA_COMPLETA = "Rua Dona Francisca, 1285, Centro, Joinville, SC, 89201-250";
    private static final String CONSULTA_FALLBACK = "Rua Dona Francisca, Joinville, SC, Brasil";

    @Mock
    private OpenStreetMapClient openStreetMapClient;

    @Mock
    private Clock clock;

    @Mock
    private OpenStreetMapService.Espera espera;

    private OpenStreetMapService openStreetMapService;

    @BeforeEach
    void setUp() {
        openStreetMapService = new OpenStreetMapService(openStreetMapClient, AGENT, INTERVALO_MS, clock, espera);
        avancarPara(T0);
    }

    @AfterEach
    void limparInterrupcao() {
        // O teste de interrupção deixa a flag ligada; não pode vazar para o próximo teste.
        Thread.interrupted();
    }

    private void avancarPara(Instant instante) {
        when(clock.instant()).thenReturn(instante);
    }

    private void responder(String query, List<Map<String, Object>> resultado) {
        when(openStreetMapClient.search(eq(query), eq("json"), anyInt(), anyInt(), eq("br"), eq(AGENT)))
                .thenReturn(resultado);
    }

    private void falhar(String query, FeignException erro) {
        when(openStreetMapClient.search(eq(query), eq("json"), anyInt(), anyInt(), eq("br"), eq(AGENT)))
                .thenThrow(erro);
    }

    private static Map<String, Object> local(String lat, String lon) {
        Map<String, Object> location = new HashMap<>();
        location.put("lat", lat);
        location.put("lon", lon);
        return location;
    }

    private static Map<String, Object> localComIso(String lat, String lon, String iso) {
        Map<String, Object> location = local(lat, lon);
        location.put("address", Map.of("city", "Qualquer", "ISO3166-2-lvl4", iso));
        return location;
    }

    private static FeignException tooManyRequests() {
        Request request = Request.create(Request.HttpMethod.GET, "https://nominatim.openstreetmap.org/search",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.TooManyRequests("Too Many Requests", request, null, null);
    }

    private Optional<OpenStreetMapService.Coordinates> geocodificar(String uf) {
        return openStreetMapService.geocodificarComFallback(ADDRESS, DISTRICT, CITY, STATE, CEP, uf);
    }

    // ---- getCoordinates (cadastro manual) ----

    @Test
    void getCoordinates_ShouldReturnCoordinates_WhenResultExists() {
        responder("São Paulo", List.of(local("-23.550520", "-46.633308")));

        Optional<OpenStreetMapService.Coordinates> result = openStreetMapService.getCoordinates("São Paulo");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(new BigDecimal("-23.550520"));
        assertThat(result.get().longitude()).isEqualTo(new BigDecimal("-46.633308"));
    }

    @Test
    void getCoordinates_ShouldReturnEmpty_WhenResultIsEmpty() {
        responder("Unknown", Collections.emptyList());

        Optional<OpenStreetMapService.Coordinates> result = openStreetMapService.getCoordinates("Unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void getCoordinates_ShouldNotValidateUf_EvenWhenResultIsFromAnotherState() {
        responder("Curitiba", List.of(localComIso("-25.42", "-49.27", "BR-PR")));

        assertThat(openStreetMapService.getCoordinates("Curitiba")).isPresent();
    }

    /**
     * Os dois valores são 1, então este teste não distingue uma troca em tempo de execução.
     * Ele fixa as posições pelos NOMES das constantes: quem inverter addressdetails e limit
     * na chamada precisa inverter aqui também, e a revisão vê a troca.
     */
    @Test
    void search_ShouldSendCountryCodesBr_WithAddressDetailAndLimitInTheirPositions() {
        responder("Joinville", List.of(local("-26.30", "-48.84")));

        openStreetMapService.getCoordinates("Joinville");

        verify(openStreetMapClient).search(
                "Joinville", FORMAT_JSON, DEFAULT_ADDRESS_DETAIL, DEFAULT_LIMIT, COUNTRY_CODES_BRASIL, AGENT);
        assertThat(COUNTRY_CODES_BRASIL).isEqualTo("br");
    }

    // ---- throttle ----

    @Test
    void throttle_ShouldNotWait_OnTheFirstCall() {
        responder("A", List.of());

        openStreetMapService.getCoordinates("A");

        verifyNoInteractions(espera);
    }

    @Test
    void throttle_ShouldWaitTheFullInterval_ForTwoConsecutiveCalls() throws InterruptedException {
        responder("A", List.of());

        openStreetMapService.getCoordinates("A");
        openStreetMapService.getCoordinates("A");

        verify(espera).aguardar(Duration.ofMillis(INTERVALO_MS));
    }

    @Test
    void throttle_ShouldWaitOnlyTheRemainder_WhenPartOfTheIntervalHasPassed() throws InterruptedException {
        responder("A", List.of());

        openStreetMapService.getCoordinates("A");
        avancarPara(T0.plusMillis(400));
        openStreetMapService.getCoordinates("A");

        verify(espera).aguardar(Duration.ofMillis(700));
    }

    @Test
    void throttle_ShouldNotWait_WhenCallsAreSpacedBeyondTheInterval() {
        responder("A", List.of());

        openStreetMapService.getCoordinates("A");
        avancarPara(T0.plusMillis(2000));
        openStreetMapService.getCoordinates("A");

        verifyNoInteractions(espera);
    }

    @Test
    void throttle_ShouldWaitBeforeCallingTheClient() throws InterruptedException {
        responder("A", List.of());

        openStreetMapService.getCoordinates("A");
        openStreetMapService.getCoordinates("A");

        InOrder ordem = inOrder(espera, openStreetMapClient);
        ordem.verify(openStreetMapClient).search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
        ordem.verify(espera).aguardar(any());
        ordem.verify(openStreetMapClient).search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void throttle_ShouldStackReservations_ForCallsAtTheSameInstant() throws InterruptedException {
        responder("A", List.of());

        openStreetMapService.getCoordinates("A");
        openStreetMapService.getCoordinates("A");
        openStreetMapService.getCoordinates("A");

        // O relógio não andou: a terceira reserva fica dois intervalos à frente.
        verify(espera).aguardar(Duration.ofMillis(INTERVALO_MS));
        verify(espera).aguardar(Duration.ofMillis(2 * INTERVALO_MS));
    }

    @Test
    void throttle_ShouldRestoreInterruptFlagAndThrow_WhenWaitIsInterrupted() throws InterruptedException {
        responder("A", List.of());
        doThrow(new InterruptedException()).when(espera).aguardar(any());

        openStreetMapService.getCoordinates("A");

        assertThatThrownBy(() -> openStreetMapService.getCoordinates("A"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(openStreetMapClient, times(1))
                .search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    // ---- geocodificarComFallback ----

    @Test
    void geocodificarComFallback_ShouldNotFallBack_WhenFullAddressMatchesTheUf() {
        responder(CONSULTA_COMPLETA, List.of(localComIso("-26.30", "-48.84", "BR-SC")));

        Optional<OpenStreetMapService.Coordinates> result = geocodificar("SC");

        assertThat(result).contains(new OpenStreetMapService.Coordinates(
                new BigDecimal("-26.30"), new BigDecimal("-48.84")));
        verify(openStreetMapClient, times(1))
                .search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void geocodificarComFallback_ShouldFallBackToStreetCityUf_WhenFullAddressIsEmpty() {
        responder(CONSULTA_COMPLETA, List.of());
        responder(CONSULTA_FALLBACK, List.of(localComIso("-26.31", "-48.85", "BR-SC")));

        Optional<OpenStreetMapService.Coordinates> result = geocodificar("SC");

        assertThat(result).contains(new OpenStreetMapService.Coordinates(
                new BigDecimal("-26.31"), new BigDecimal("-48.85")));
        verify(openStreetMapClient).search(eq(CONSULTA_FALLBACK), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void geocodificarComFallback_ShouldRejectResultFromAnotherUf_AndFallBack() {
        responder(CONSULTA_COMPLETA, List.of(localComIso("-25.42", "-49.27", "BR-PR")));
        responder(CONSULTA_FALLBACK, List.of(localComIso("-26.31", "-48.85", "BR-SC")));

        Optional<OpenStreetMapService.Coordinates> result = geocodificar("SC");

        assertThat(result).contains(new OpenStreetMapService.Coordinates(
                new BigDecimal("-26.31"), new BigDecimal("-48.85")));
    }

    @Test
    void geocodificarComFallback_ShouldAccept_WhenIsoFieldIsMissing() {
        Map<String, Object> semIso = local("-26.30", "-48.84");
        semIso.put("address", Map.of("city", "Joinville", "country_code", "br"));
        responder(CONSULTA_COMPLETA, List.of(semIso));

        assertThat(geocodificar("SC")).isPresent();
        verify(openStreetMapClient, never())
                .search(eq(CONSULTA_FALLBACK), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void geocodificarComFallback_ShouldAccept_WhenAddressIsMissing() {
        responder(CONSULTA_COMPLETA, List.of(local("-26.30", "-48.84")));

        assertThat(geocodificar("SC")).isPresent();
    }

    @Test
    void geocodificarComFallback_ShouldAcceptWithoutException_WhenAddressHasUnexpectedType() {
        Map<String, Object> addressString = local("-26.30", "-48.84");
        addressString.put("address", "Rua Dona Francisca, Joinville");
        responder(CONSULTA_COMPLETA, List.of(addressString));

        assertThat(geocodificar("SC")).isPresent();
    }

    @Test
    void geocodificarComFallback_ShouldAccept_WhenIsoFieldHasUnexpectedType() {
        Map<String, Object> isoNumerico = local("-26.30", "-48.84");
        isoNumerico.put("address", Map.of("ISO3166-2-lvl4", 42));
        responder(CONSULTA_COMPLETA, List.of(isoNumerico));

        assertThat(geocodificar("SC")).isPresent();
    }

    @Test
    void geocodificarComFallback_ShouldNormalizeUfCaseAndSpaces() {
        responder(CONSULTA_COMPLETA, List.of(localComIso("-26.30", "-48.84", "BR-SC")));

        assertThat(geocodificar(" sc ")).isPresent();
    }

    @Test
    void geocodificarComFallback_ShouldReturnEmpty_WhenFallbackIsEmpty() {
        responder(CONSULTA_COMPLETA, List.of());
        responder(CONSULTA_FALLBACK, List.of());

        assertThat(geocodificar("SC")).isEmpty();
    }

    @Test
    void geocodificarComFallback_ShouldReturnEmpty_WhenFallbackIsRejectedByUf() {
        responder(CONSULTA_COMPLETA, List.of(localComIso("-25.42", "-49.27", "BR-PR")));
        responder(CONSULTA_FALLBACK, List.of(localComIso("-25.43", "-49.28", "BR-PR")));

        assertThat(geocodificar("SC")).isEmpty();
    }

    @Test
    void geocodificarComFallback_ShouldSkipFallback_WhenStreetIsBlank() {
        when(openStreetMapClient.search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of());

        Optional<OpenStreetMapService.Coordinates> result =
                openStreetMapService.geocodificarComFallback(" , 123", DISTRICT, CITY, STATE, CEP, "SC");

        assertThat(result).isEmpty();
        verify(openStreetMapClient, times(1))
                .search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void geocodificarComFallback_ShouldUseWholeAddressAsStreet_WhenThereIsNoComma() {
        String semVirgula = "Rodovia BR-101 KM 206";
        responder(String.format("%s, %s, %s, %s, %s", semVirgula, DISTRICT, CITY, STATE, CEP), List.of());
        responder("Rodovia BR-101 KM 206, Joinville, SC, Brasil", List.of(local("-26.2", "-48.8")));

        assertThat(openStreetMapService.geocodificarComFallback(semVirgula, DISTRICT, CITY, STATE, CEP, "SC"))
                .isPresent();
    }

    @Test
    void geocodificarComFallback_ShouldThrottleBothQueries() throws InterruptedException {
        responder(CONSULTA_COMPLETA, List.of());
        responder(CONSULTA_FALLBACK, List.of());

        geocodificar("SC");

        verify(espera).aguardar(Duration.ofMillis(INTERVALO_MS));
    }

    @Test
    void geocodificarComFallback_ShouldPropagateFeignException_FromTheFirstQuery_WithoutFallingBack() {
        FeignException erro = tooManyRequests();
        falhar(CONSULTA_COMPLETA, erro);

        assertThatThrownBy(() -> geocodificar("SC")).isSameAs(erro);
        verify(openStreetMapClient, times(1))
                .search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void geocodificarComFallback_ShouldPropagateFeignException_FromTheFallback() {
        FeignException erro = tooManyRequests();
        responder(CONSULTA_COMPLETA, List.of());
        falhar(CONSULTA_FALLBACK, erro);

        assertThatThrownBy(() -> geocodificar("SC")).isSameAs(erro);
    }
}
