package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.OpenStreetMapClient;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import com.github.api_abastecefacil.service.OpenStreetMapService.Geocodificacao;
import com.github.api_abastecefacil.service.OpenStreetMapService.Origem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.COUNTRY_CODES_BRASIL;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.DEFAULT_ADDRESS_DETAIL;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.DEFAULT_LIMIT;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.FORMAT_JSON;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.NOME_POR_UF;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.PAIS_BRASIL;
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
 * Geocodificação pelo Nominatim: throttle, busca estruturada, fallback em texto livre e regra
 * de UF.
 *
 * <p><b>Sem CEP em nenhuma consulta, por construção:</b> {@code geocodificarComFallback} não
 * recebe CEP. Os testes de parâmetros conferem a chamada inteira ao client, argumento por
 * argumento, e é isso que garante que nada além do previsto vai ao Nominatim.
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
    private static final String CITY = "Joinville";
    private static final String UF = "SC";
    private static final String STREET = "1285 Rua Dona Francisca";
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

    /** Responde a qualquer busca estruturada. Os argumentos são conferidos à parte. */
    private void responderEstruturada(List<Map<String, Object>> resultado) {
        when(openStreetMapClient.searchEstruturada(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(resultado);
    }

    private void falharEstruturada(FeignException erro) {
        when(openStreetMapClient.searchEstruturada(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(erro);
    }

    private void responderTextoLivre(String query, List<Map<String, Object>> resultado) {
        when(openStreetMapClient.search(eq(query), eq("json"), anyInt(), anyInt(), eq("br"), eq(AGENT)))
                .thenReturn(resultado);
    }

    private void falharTextoLivre(String query, FeignException erro) {
        when(openStreetMapClient.search(eq(query), eq("json"), anyInt(), anyInt(), eq("br"), eq(AGENT)))
                .thenThrow(erro);
    }

    private void verificarStreet(String street) {
        verify(openStreetMapClient).searchEstruturada(eq(street), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyString(), anyString());
    }

    private void verificarSemTextoLivre() {
        verify(openStreetMapClient, never())
                .search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
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

    private static Coordinates coordenadas(String lat, String lon) {
        return new Coordinates(new BigDecimal(lat), new BigDecimal(lon));
    }

    private static FeignException tooManyRequests() {
        Request request = Request.create(Request.HttpMethod.GET, "https://nominatim.openstreetmap.org/search",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.TooManyRequests("Too Many Requests", request, null, null);
    }

    private Geocodificacao geocodificar() {
        return openStreetMapService.geocodificarComFallback(ADDRESS, CITY, UF);
    }

    // ---- parâmetros enviados ----

    /**
     * Os valores de addressdetails e limit são 1, então este teste não distingue uma troca em
     * tempo de execução. Ele fixa as posições pelos NOMES das constantes: quem inverter os
     * dois na chamada precisa inverter aqui também, e a revisão vê a troca (§9, item 18).
     */
    @Test
    void estruturada_ShouldSendStreetCityStateNameAndCountry_WithoutQuery() {
        responderEstruturada(List.of(localComIso("-26.30", "-48.84", "BR-SC")));

        geocodificar();

        verify(openStreetMapClient).searchEstruturada(STREET, CITY, "Santa Catarina", PAIS_BRASIL, FORMAT_JSON,
                DEFAULT_ADDRESS_DETAIL, DEFAULT_LIMIT, COUNTRY_CODES_BRASIL, AGENT);
        verificarSemTextoLivre();
        assertThat(PAIS_BRASIL).isEqualTo("Brasil");
        assertThat(COUNTRY_CODES_BRASIL).isEqualTo("br");
    }

    /** Mesmo cuidado de posição do teste acima, para o método em texto livre. */
    @Test
    void textoLivre_ShouldSendFallbackQueryWithoutCep_WithAddressDetailAndLimitInTheirPositions() {
        responderEstruturada(List.of());
        responderTextoLivre(CONSULTA_FALLBACK, List.of());

        geocodificar();

        verify(openStreetMapClient).search(
                CONSULTA_FALLBACK, FORMAT_JSON, DEFAULT_ADDRESS_DETAIL, DEFAULT_LIMIT, COUNTRY_CODES_BRASIL, AGENT);
    }

    @Test
    void nomePorUf_ShouldHaveAllTwentySevenFederativeUnits() {
        assertThat(NOME_POR_UF).hasSize(27);
        assertThat(NOME_POR_UF).containsEntry("SC", "Santa Catarina").containsEntry("DF", "Distrito Federal");
    }

    // ---- street: número e via ----

    @Test
    void street_ShouldOmitNumber_WhenItIsSemNumero() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        openStreetMapService.geocodificarComFallback("AVENIDA CORONEL RUPP, S/N", "Catanduvas", UF);

        verificarStreet("AVENIDA CORONEL RUPP");
    }

    @Test
    void street_ShouldOmitNumber_WhenItIsBlank() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        openStreetMapService.geocodificarComFallback("Rua das Palmeiras, ", CITY, UF);

        verificarStreet("Rua das Palmeiras");
    }

    @Test
    void street_ShouldOmitNumber_WhenItIsAKilometerMark() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        openStreetMapService.geocodificarComFallback("RODOVIA BR-101, KM 206", CITY, UF);

        verificarStreet("RODOVIA BR-101");
    }

    @Test
    void street_ShouldKeepNumberWithLetter() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        openStreetMapService.geocodificarComFallback("Rua Blumenau, 1285 E", CITY, UF);
        openStreetMapService.geocodificarComFallback("Rua Blumenau, 1285E", CITY, UF);

        verificarStreet("1285 E Rua Blumenau");
        verificarStreet("1285E Rua Blumenau");
    }

    @Test
    void street_ShouldUseWholeAddressWithoutNumber_WhenThereIsNoComma() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        openStreetMapService.geocodificarComFallback("Rodovia BR-101 KM 206", CITY, UF);

        verificarStreet("Rodovia BR-101 KM 206");
    }

    @Test
    void geocodificar_ShouldNotQueryAtAll_WhenStreetIsBlank() {
        Geocodificacao resultado = openStreetMapService.geocodificarComFallback(" , 123", CITY, UF);

        assertThat(resultado.origem()).isEqualTo(Origem.NAO_LOCALIZADO);
        assertThat(resultado.coordenadas()).isEmpty();
        assertThat(resultado.rejeitadosPelaUf()).isZero();
        verifyNoInteractions(openStreetMapClient, espera);
    }

    // ---- estado: sigla e nome ----

    @Test
    void estado_ShouldNormalizeUfCaseAndSpaces() {
        responderEstruturada(List.of(localComIso("-26.30", "-48.84", "BR-SC")));

        Geocodificacao resultado = openStreetMapService.geocodificarComFallback(ADDRESS, CITY, " sc ");

        assertThat(resultado.coordenadas()).isPresent();
        verify(openStreetMapClient).searchEstruturada(anyString(), anyString(), eq("Santa Catarina"), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void estado_ShouldAcceptFullNameWithoutAccentOrCase_AndUseTheUfInTheFallback() {
        responderEstruturada(List.of());
        responderTextoLivre("Rua Dona Francisca, Joinville, SC, Brasil", List.of(localComIso("-26.31", "-48.85", "BR-SC")));

        Geocodificacao resultado = openStreetMapService.geocodificarComFallback(ADDRESS, CITY, "santa catarina");

        assertThat(resultado.origem()).isEqualTo(Origem.TEXTO_LIVRE);
        assertThat(resultado.rejeitadosPelaUf()).isZero();
        verify(openStreetMapClient).searchEstruturada(anyString(), anyString(), eq("Santa Catarina"), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void estado_ShouldResolveFullNameWithAccent() {
        responderEstruturada(List.of(localComIso("-23.55", "-46.63", "BR-SP")));

        Geocodificacao resultado = openStreetMapService.geocodificarComFallback("Rua Augusta, 100", "São Paulo",
                "SÃO PAULO");

        assertThat(resultado.origem()).isEqualTo(Origem.ESTRUTURADA);
    }

    @Test
    void estado_ShouldRejectAResultFromAKnownUf_WhenTheGivenStateIsUnknown() {
        responderEstruturada(List.of(localComIso("-26.30", "-48.84", "BR-SC")));
        responderTextoLivre("Rua Dona Francisca, Joinville, XX, Brasil", List.of(localComIso("-26.30", "-48.84", "BR-SC")));

        Geocodificacao resultado = openStreetMapService.geocodificarComFallback(ADDRESS, CITY, "xx");

        assertThat(resultado.origem()).isEqualTo(Origem.NAO_LOCALIZADO);
        assertThat(resultado.rejeitadosPelaUf()).isEqualTo(2);
        verify(openStreetMapClient).searchEstruturada(anyString(), anyString(), eq("XX"), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    // ---- ordem das consultas e desfechos ----

    @Test
    void geocodificar_ShouldNotFallBack_WhenStructuredSearchMatchesTheUf() {
        responderEstruturada(List.of(localComIso("-26.30", "-48.84", "BR-SC")));

        Geocodificacao resultado = geocodificar();

        assertThat(resultado.origem()).isEqualTo(Origem.ESTRUTURADA);
        assertThat(resultado.coordenadas()).contains(coordenadas("-26.30", "-48.84"));
        assertThat(resultado.rejeitadosPelaUf()).isZero();
        verificarSemTextoLivre();
    }

    @Test
    void geocodificar_ShouldFallBackToFreeText_WhenStructuredSearchIsEmpty() {
        responderEstruturada(List.of());
        responderTextoLivre(CONSULTA_FALLBACK, List.of(localComIso("-26.31", "-48.85", "BR-SC")));

        Geocodificacao resultado = geocodificar();

        assertThat(resultado.origem()).isEqualTo(Origem.TEXTO_LIVRE);
        assertThat(resultado.coordenadas()).contains(coordenadas("-26.31", "-48.85"));
        assertThat(resultado.rejeitadosPelaUf()).isZero();
    }

    @Test
    void geocodificar_ShouldFallBack_WhenStructuredResultIsFromAnotherUf() {
        responderEstruturada(List.of(localComIso("-25.42", "-49.27", "BR-PR")));
        responderTextoLivre(CONSULTA_FALLBACK, List.of(localComIso("-26.31", "-48.85", "BR-SC")));

        Geocodificacao resultado = geocodificar();

        assertThat(resultado.origem()).isEqualTo(Origem.TEXTO_LIVRE);
        assertThat(resultado.coordenadas()).contains(coordenadas("-26.31", "-48.85"));
        assertThat(resultado.rejeitadosPelaUf()).isEqualTo(1);
    }

    @Test
    void geocodificar_ShouldBeNotFound_WhenBothQueriesAreEmpty() {
        responderEstruturada(List.of());
        responderTextoLivre(CONSULTA_FALLBACK, List.of());

        Geocodificacao resultado = geocodificar();

        assertThat(resultado.origem()).isEqualTo(Origem.NAO_LOCALIZADO);
        assertThat(resultado.coordenadas()).isEmpty();
        assertThat(resultado.rejeitadosPelaUf()).isZero();
    }

    @Test
    void geocodificar_ShouldBeNotFound_AndCountOneRejection_WhenStructuredIsRejectedAndFallbackIsEmpty() {
        responderEstruturada(List.of(localComIso("-25.42", "-49.27", "BR-PR")));
        responderTextoLivre(CONSULTA_FALLBACK, List.of());

        Geocodificacao resultado = geocodificar();

        assertThat(resultado.origem()).isEqualTo(Origem.NAO_LOCALIZADO);
        assertThat(resultado.rejeitadosPelaUf()).isEqualTo(1);
    }

    @Test
    void geocodificar_ShouldBeNotFound_AndCountTwoRejections_WhenBothAreFromAnotherUf() {
        responderEstruturada(List.of(localComIso("-25.42", "-49.27", "BR-PR")));
        responderTextoLivre(CONSULTA_FALLBACK, List.of(localComIso("-25.43", "-49.28", "BR-PR")));

        Geocodificacao resultado = geocodificar();

        assertThat(resultado.origem()).isEqualTo(Origem.NAO_LOCALIZADO);
        assertThat(resultado.coordenadas()).isEmpty();
        assertThat(resultado.rejeitadosPelaUf()).isEqualTo(2);
    }

    // ---- leitura defensiva da UF ----

    @Test
    void uf_ShouldAccept_WhenIsoFieldIsMissing() {
        Map<String, Object> semIso = local("-26.30", "-48.84");
        semIso.put("address", Map.of("city", "Joinville", "country_code", "br"));
        responderEstruturada(List.of(semIso));

        assertThat(geocodificar().origem()).isEqualTo(Origem.ESTRUTURADA);
        verificarSemTextoLivre();
    }

    @Test
    void uf_ShouldAccept_WhenAddressIsMissing() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        assertThat(geocodificar().coordenadas()).isPresent();
    }

    @Test
    void uf_ShouldAcceptWithoutException_WhenAddressHasUnexpectedType() {
        Map<String, Object> addressString = local("-26.30", "-48.84");
        addressString.put("address", "Rua Dona Francisca, Joinville");
        responderEstruturada(List.of(addressString));

        assertThat(geocodificar().coordenadas()).isPresent();
    }

    @Test
    void uf_ShouldAccept_WhenIsoFieldHasUnexpectedType() {
        Map<String, Object> isoNumerico = local("-26.30", "-48.84");
        isoNumerico.put("address", Map.of("ISO3166-2-lvl4", 42));
        responderEstruturada(List.of(isoNumerico));

        assertThat(geocodificar().coordenadas()).isPresent();
    }

    // ---- contrato de erro ----

    @Test
    void geocodificar_ShouldPropagateFeignException_FromTheStructuredSearch_WithoutFallingBack() {
        FeignException erro = tooManyRequests();
        falharEstruturada(erro);

        assertThatThrownBy(this::geocodificar).isSameAs(erro);
        verificarSemTextoLivre();
    }

    @Test
    void geocodificar_ShouldPropagateFeignException_FromTheFallback() {
        FeignException erro = tooManyRequests();
        responderEstruturada(List.of());
        falharTextoLivre(CONSULTA_FALLBACK, erro);

        assertThatThrownBy(this::geocodificar).isSameAs(erro);
    }

    // ---- throttle ----

    @Test
    void throttle_ShouldNotWait_OnTheFirstCall() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        geocodificar();

        verifyNoInteractions(espera);
    }

    @Test
    void throttle_ShouldWaitTheFullInterval_ForTwoConsecutiveCalls() throws InterruptedException {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        geocodificar();
        geocodificar();

        verify(espera).aguardar(Duration.ofMillis(INTERVALO_MS));
    }

    @Test
    void throttle_ShouldWaitOnlyTheRemainder_WhenPartOfTheIntervalHasPassed() throws InterruptedException {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        geocodificar();
        avancarPara(T0.plusMillis(400));
        geocodificar();

        verify(espera).aguardar(Duration.ofMillis(700));
    }

    @Test
    void throttle_ShouldNotWait_WhenCallsAreSpacedBeyondTheInterval() {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        geocodificar();
        avancarPara(T0.plusMillis(2000));
        geocodificar();

        verifyNoInteractions(espera);
    }

    @Test
    void throttle_ShouldWaitBeforeCallingTheClient() throws InterruptedException {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        geocodificar();
        geocodificar();

        InOrder ordem = inOrder(espera, openStreetMapClient);
        ordem.verify(openStreetMapClient).searchEstruturada(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
        ordem.verify(espera).aguardar(any());
        ordem.verify(openStreetMapClient).searchEstruturada(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void throttle_ShouldStackReservations_ForCallsAtTheSameInstant() throws InterruptedException {
        responderEstruturada(List.of(local("-26.30", "-48.84")));

        geocodificar();
        geocodificar();
        geocodificar();

        // O relógio não andou: a terceira reserva fica dois intervalos à frente.
        verify(espera).aguardar(Duration.ofMillis(INTERVALO_MS));
        verify(espera).aguardar(Duration.ofMillis(2 * INTERVALO_MS));
    }

    @Test
    void throttle_ShouldRestoreInterruptFlagAndThrow_WhenWaitIsInterrupted() throws InterruptedException {
        responderEstruturada(List.of(local("-26.30", "-48.84")));
        doThrow(new InterruptedException()).when(espera).aguardar(any());

        geocodificar();

        assertThatThrownBy(this::geocodificar)
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(openStreetMapClient, times(1)).searchEstruturada(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void throttle_ShouldApplyToBothQueries() throws InterruptedException {
        responderEstruturada(List.of());
        responderTextoLivre(CONSULTA_FALLBACK, List.of());

        geocodificar();

        InOrder ordem = inOrder(espera, openStreetMapClient);
        ordem.verify(openStreetMapClient).searchEstruturada(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString());
        ordem.verify(espera).aguardar(Duration.ofMillis(INTERVALO_MS));
        ordem.verify(openStreetMapClient).search(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }
}
