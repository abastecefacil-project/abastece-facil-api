package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.OpenStreetMapClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.GasStationConstants.ADDRESS_FORMAT;
import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.*;

/**
 * Geocodificação de endereço pelo Nominatim (OpenStreetMap).
 *
 * <p><b>Toda consulta passa por um throttle global.</b> A política de uso do Nominatim
 * permite no máximo uma requisição por segundo, e a importação em lote da planilha de
 * postos faz centenas em sequência. O intervalo mínimo vale também para o cadastro e a
 * edição manuais: só assim os dois fluxos rodando ao mesmo tempo continuam dentro do
 * limite.
 *
 * <p>Cada chamada <b>reserva</b> o próximo horário livre sob a trava e espera <b>fora</b>
 * dela. Chamadas concorrentes saem espaçadas sem que nenhuma thread durma segurando a
 * trava.
 *
 * <p>O {@link Clock} e a {@link Espera} são injetados pelo mesmo motivo do
 * {@code RateLimitService}: com {@code Instant.now()} e {@code Thread.sleep} fixos no
 * código, provar o espaçamento exigiria um teste que dorme de verdade.
 *
 * <p><b>Limitação conhecida: o estado é por instância.</b> Com mais de um nó do backend,
 * cada um espaça só as próprias chamadas, e o limite de 1 req/s ao Nominatim deixa de ser
 * garantido. Reiniciar a aplicação zera a reserva.
 */
@Service
public class OpenStreetMapService {

    private final OpenStreetMapClient openStreetMapClient;
    private final String userAgent;
    private final Duration intervaloMinimo;
    private final Clock clock;
    private final Espera espera;

    private final Object trava = new Object();
    /** Horário reservado pela última chamada; {@code null} até a primeira. */
    private Instant ultimaReserva;

    @Autowired
    public OpenStreetMapService(
            OpenStreetMapClient openStreetMapClient,
            @Value("${openstreetmap-api.user-agent}") String userAgent,
            @Value("${openstreetmap-api.intervalo-minimo-ms:1100}") long intervaloMinimoMs,
            Clock clock
    ) {
        this(openStreetMapClient, userAgent, intervaloMinimoMs, clock, Thread::sleep);
    }

    OpenStreetMapService(
            OpenStreetMapClient openStreetMapClient,
            String userAgent,
            long intervaloMinimoMs,
            Clock clock,
            Espera espera
    ) {
        this.openStreetMapClient = openStreetMapClient;
        this.userAgent = userAgent;
        this.intervaloMinimo = Duration.ofMillis(intervaloMinimoMs);
        this.clock = clock;
        this.espera = espera;
    }

    public Optional<Coordinates> getCoordinates(String query) {
        List<Map<String, Object>> results = searchLocation(query);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return extractCoordinatesFromFirstResult(results);
    }

    /**
     * Geocodificação da importação em lote: endereço completo, e só se ele falhar, a
     * consulta simplificada {@code "<via>, <cidade>, <UF>, Brasil"}.
     *
     * <p>Um resultado só é aceito se a UF conferir com a do posto. O código ISO da
     * subdivisão ({@code address.ISO3166-2-lvl4}, ex.: {@code "BR-SC"}) é comparado com
     * {@code "BR-" + uf}. Quando o campo não vem, ou vem num formato inesperado, o
     * resultado é aceito: falta de metadado não é motivo para recusar. Essa validação vale
     * <b>só aqui</b>. O cadastro manual usa {@link #getCoordinates} e não a aplica.
     *
     * <p>Se a via (o trecho de {@code address} antes da vírgula) ficar vazia, o fallback
     * não é feito. A consulta seria só cidade e UF, e devolveria o centro da cidade como se
     * fosse o posto.
     *
     * <p><b>Contrato de erro.</b> {@code Optional} vazio significa apenas "não encontrado
     * ou rejeitado pela UF", nunca "erro de comunicação". Uma {@code FeignException}
     * (timeout, 429, 5xx...) é <b>propagada</b>, tanto da primeira consulta quanto do
     * fallback. Se a primeira falha por erro, o fallback não é tentado: com o Nominatim fora
     * ou bloqueando, a segunda consulta também falharia e só gastaria mais uma requisição.
     *
     * <p>As duas consultas passam pelo throttle.
     */
    public Optional<Coordinates> geocodificarComFallback(
            String address, String district, String city, String state, String cep, String uf
    ) {
        String consultaCompleta = String.format(ADDRESS_FORMAT, address, district, city, state, cep);
        Optional<Coordinates> completa = primeiroResultadoDaUf(consultaCompleta, uf);
        if (completa.isPresent()) {
            return completa;
        }

        String via = viaSemNumero(address);
        if (via.isEmpty()) {
            return Optional.empty();
        }

        String consultaSimplificada = String.format(FALLBACK_FORMAT, via, city, ufNormalizada(uf));
        return primeiroResultadoDaUf(consultaSimplificada, uf);
    }

    private Optional<Coordinates> primeiroResultadoDaUf(String query, String uf) {
        List<Map<String, Object>> results = searchLocation(query);
        if (results.isEmpty() || !ufConfere(results.get(0), uf)) {
            return Optional.empty();
        }
        return extractCoordinatesFromFirstResult(results);
    }

    /**
     * Leitura defensiva: {@code address} ausente ou que não seja {@code Map}, e código ISO
     * ausente ou que não seja {@code String}, contam como "campo não veio" e o resultado é
     * aceito. Nenhum cast é feito sem conferir o tipo antes.
     */
    private boolean ufConfere(Map<String, Object> resultado, String uf) {
        if (!(resultado.get(ADDRESS_KEY) instanceof Map<?, ?> endereco)) {
            return true;
        }
        if (!(endereco.get(ISO_SUBDIVISAO_KEY) instanceof String codigoIso)) {
            return true;
        }
        return codigoIso.trim().equalsIgnoreCase(PREFIXO_ISO_BRASIL + ufNormalizada(uf));
    }

    /** O {@code state} da planilha mantém a caixa de origem, então a UF é normalizada aqui. */
    private static String ufNormalizada(String uf) {
        return uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
    }

    private static String viaSemNumero(String address) {
        if (address == null) {
            return "";
        }
        int separador = address.indexOf(SEPARADOR_NUMERO);
        String via = separador < 0 ? address : address.substring(0, separador);
        return via.trim();
    }

    private List<Map<String, Object>> searchLocation(String query) {
        aguardarVez();
        return openStreetMapClient.search(
                query,
                FORMAT_JSON,
                DEFAULT_ADDRESS_DETAIL,
                DEFAULT_LIMIT,
                COUNTRY_CODES_BRASIL,
                userAgent
        );
    }

    private void aguardarVez() {
        Duration ate = reservarHorario();
        if (!ate.isPositive()) {
            return;
        }
        try {
            espera.aguardar(ate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(INTERRUPCAO_ESPERA_MESSAGE, e);
        }
    }

    /**
     * Reserva o próximo horário livre e devolve quanto falta até ele. Só a reserva fica sob
     * a trava. A espera acontece fora dela, para que uma thread dormindo não bloqueie as
     * outras de reservarem o horário seguinte.
     */
    private Duration reservarHorario() {
        synchronized (trava) {
            Instant agora = clock.instant();
            Instant horario = agora;
            if (ultimaReserva != null) {
                Instant liberacao = ultimaReserva.plus(intervaloMinimo);
                if (liberacao.isAfter(agora)) {
                    horario = liberacao;
                }
            }
            ultimaReserva = horario;
            return Duration.between(agora, horario);
        }
    }

    private Optional<Coordinates> extractCoordinatesFromFirstResult(List<Map<String, Object>> results) {
        Map<String, Object> firstLocation = results.get(0);
        BigDecimal latitude = parseCoordinate(firstLocation.get(LATITUDE_KEY));
        BigDecimal longitude = parseCoordinate(firstLocation.get(LONGITUDE_KEY));
        return Optional.of(new Coordinates(latitude, longitude));
    }

    private BigDecimal parseCoordinate(Object value) {
        if (value instanceof String stringValue) {
            return new BigDecimal(stringValue);
        }
        throw new IllegalArgumentException("Formato de coordenada inválido: " + value);
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }

    /** Mecanismo de espera do throttle. Em produção é {@code Thread::sleep}; no teste, um mock. */
    @FunctionalInterface
    interface Espera {
        void aguardar(Duration duracao) throws InterruptedException;
    }
}
