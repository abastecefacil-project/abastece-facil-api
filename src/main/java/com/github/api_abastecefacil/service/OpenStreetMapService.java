package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.OpenStreetMapClient;
import com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos;
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

    /**
     * Geocodificação de posto, usada pela importação em lote <b>e</b> pelo cadastro e edição
     * manuais: no máximo duas consultas, nenhuma com CEP.
     *
     * <ol>
     *   <li><b>Busca estruturada</b>: {@code street} = {@code "<número> <via>"}, ou só a via
     *       quando o número não é predial ({@code "S/N"}, vazio, {@code "KM 206"} — ver
     *       {@code NUMERO_PREDIAL}); {@code city}; {@code state} com o <b>nome</b> do estado;
     *       {@code country} = Brasil.</li>
     *   <li>Se ela vier vazia ou for rejeitada pela UF, <b>texto livre</b>
     *       {@code "<via>, <cidade>, <UF>, Brasil"}.</li>
     * </ol>
     *
     * <p><b>Sem CEP em nenhuma das duas.</b> O Nominatim tem cobertura fraca de CEP no
     * Brasil, e cidade pequena usa CEP genérico; em texto livre ele mais atrapalhava do que
     * ajudava. O bairro também ficou de fora: a busca estruturada não tem campo para ele.
     *
     * <p><b>Regra de UF.</b> Um resultado só é aceito se a UF conferir com a do posto. O
     * código ISO da subdivisão ({@code address.ISO3166-2-lvl4}, ex.: {@code "BR-SC"}) é
     * comparado com {@code "BR-" + uf}. Quando o campo não vem, ou vem num formato
     * inesperado, o resultado é aceito: falta de metadado não é motivo para recusar. O
     * {@code uf} pode chegar como sigla ou nome por extenso (o campo do formulário manual é
     * editável) e é resolvido para a sigla antes.
     *
     * <p>Se a via (o trecho de {@code address} antes da vírgula) ficar vazia, nenhuma
     * consulta é feita: sobrariam só cidade e UF, e o resultado seria o centro da cidade
     * como se fosse o posto.
     *
     * <p><b>Contrato de erro.</b> {@link Geocodificacao#coordenadas()} vazio significa apenas
     * "não encontrado ou rejeitado pela UF", nunca "erro de comunicação". Uma
     * {@code FeignException} (timeout, 429, 5xx...) é <b>propagada</b>, de qualquer das duas
     * consultas. Se a estruturada falha por erro, o fallback não é tentado: com o Nominatim
     * fora ou bloqueando, a segunda consulta também falharia e só gastaria mais uma
     * requisição.
     *
     * <p>As duas consultas passam pelo throttle.
     *
     * @return o resultado e por qual consulta ele saiu. O retorno é por chamada, e não um
     *         contador no serviço: este bean é singleton, compartilhado com o cadastro manual
     */
    public Geocodificacao geocodificarComFallback(String address, String city, String uf) {
        String via = viaSemNumero(address);
        if (via.isEmpty()) {
            return new Geocodificacao(Origem.NAO_LOCALIZADO, null, 0);
        }
        String sigla = sigla(uf);

        String numero = numeroPredial(address);
        String street = numero == null ? via : String.format(STREET_COM_NUMERO_FORMAT, numero, via);
        Tentativa estruturada = avaliar(buscarEstruturada(street, city, NOME_POR_UF.getOrDefault(sigla, sigla)), sigla);
        if (estruturada.coordenadas() != null) {
            return new Geocodificacao(Origem.ESTRUTURADA, estruturada.coordenadas(), 0);
        }

        Tentativa textoLivre = avaliar(buscarTextoLivre(String.format(FALLBACK_FORMAT, via, city, sigla)), sigla);
        int rejeitados = (estruturada.rejeitadaPelaUf() ? 1 : 0) + (textoLivre.rejeitadaPelaUf() ? 1 : 0);
        if (textoLivre.coordenadas() != null) {
            return new Geocodificacao(Origem.TEXTO_LIVRE, textoLivre.coordenadas(), rejeitados);
        }
        return new Geocodificacao(Origem.NAO_LOCALIZADO, null, rejeitados);
    }

    private Tentativa avaliar(List<Map<String, Object>> results, String sigla) {
        if (results.isEmpty()) {
            return new Tentativa(null, false);
        }
        if (!ufConfere(results.get(0), sigla)) {
            return new Tentativa(null, true);
        }
        return new Tentativa(extractCoordinatesFromFirstResult(results), false);
    }

    /**
     * Leitura defensiva: {@code address} ausente ou que não seja {@code Map}, e código ISO
     * ausente ou que não seja {@code String}, contam como "campo não veio" e o resultado é
     * aceito. Nenhum cast é feito sem conferir o tipo antes.
     */
    private boolean ufConfere(Map<String, Object> resultado, String sigla) {
        if (!(resultado.get(ADDRESS_KEY) instanceof Map<?, ?> endereco)) {
            return true;
        }
        if (!(endereco.get(ISO_SUBDIVISAO_KEY) instanceof String codigoIso)) {
            return true;
        }
        return codigoIso.trim().equalsIgnoreCase(PREFIXO_ISO_BRASIL + sigla);
    }

    /**
     * A sigla da UF, aceitando também o nome por extenso, sem diferença de caixa e acento
     * ({@code "santa catarina"} → {@code "SC"}). Valor que não é UF nem nome conhecido segue
     * em caixa alta, como veio: a regra de UF não muda, e ele simplesmente não confere.
     */
    private static String sigla(String uf) {
        String normalizada = uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
        if (NOME_POR_UF.containsKey(normalizada)) {
            return normalizada;
        }
        String chave = NormalizadorPlanilhaPostos.chaveComparacao(uf);
        return NOME_POR_UF.entrySet().stream()
                .filter(entrada -> NormalizadorPlanilhaPostos.chaveComparacao(entrada.getValue()).equals(chave))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(normalizada);
    }

    private static String viaSemNumero(String address) {
        if (address == null) {
            return "";
        }
        int separador = address.indexOf(SEPARADOR_NUMERO);
        String via = separador < 0 ? address : address.substring(0, separador);
        return via.trim();
    }

    /** O trecho depois da vírgula, se for número predial; {@code null} em qualquer outro caso. */
    private static String numeroPredial(String address) {
        int separador = address.indexOf(SEPARADOR_NUMERO);
        if (separador < 0) {
            return null;
        }
        String numero = address.substring(separador + 1).trim();
        return NUMERO_PREDIAL.matcher(numero).matches() ? numero : null;
    }

    private List<Map<String, Object>> buscarEstruturada(String street, String city, String state) {
        aguardarVez();
        return openStreetMapClient.searchEstruturada(
                street,
                city,
                state,
                PAIS_BRASIL,
                FORMAT_JSON,
                DEFAULT_ADDRESS_DETAIL,
                DEFAULT_LIMIT,
                COUNTRY_CODES_BRASIL,
                userAgent
        );
    }

    private List<Map<String, Object>> buscarTextoLivre(String query) {
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

    private Coordinates extractCoordinatesFromFirstResult(List<Map<String, Object>> results) {
        Map<String, Object> firstLocation = results.get(0);
        BigDecimal latitude = parseCoordinate(firstLocation.get(LATITUDE_KEY));
        BigDecimal longitude = parseCoordinate(firstLocation.get(LONGITUDE_KEY));
        return new Coordinates(latitude, longitude);
    }

    private BigDecimal parseCoordinate(Object value) {
        if (value instanceof String stringValue) {
            return new BigDecimal(stringValue);
        }
        throw new IllegalArgumentException("Formato de coordenada inválido: " + value);
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }

    /** Por qual consulta o posto foi localizado. */
    public enum Origem {
        ESTRUTURADA,
        /** Só no fallback em texto livre, depois de a estruturada vir vazia ou ser rejeitada. */
        TEXTO_LIVRE,
        NAO_LOCALIZADO
    }

    /**
     * Resultado de {@link #geocodificarComFallback}.
     *
     * @param ponto            {@code null} quando {@code NAO_LOCALIZADO}; use {@link #coordenadas()}
     * @param rejeitadosPelaUf quantos resultados, de 0 a 2, foram recusados pela regra de UF.
     *                         Conta resultado, não posto: um posto recusado na estruturada e
     *                         resolvido no fallback tem origem {@code TEXTO_LIVRE} e 1 aqui
     */
    public record Geocodificacao(Origem origem, Coordinates ponto, int rejeitadosPelaUf) {

        public Optional<Coordinates> coordenadas() {
            return Optional.ofNullable(ponto);
        }
    }

    /** Uma das duas consultas: as coordenadas, ou se o primeiro resultado foi recusado pela UF. */
    private record Tentativa(Coordinates coordenadas, boolean rejeitadaPelaUf) {
    }

    /** Mecanismo de espera do throttle. Em produção é {@code Thread::sleep}; no teste, um mock. */
    @FunctionalInterface
    interface Espera {
        void aguardar(Duration duracao) throws InterruptedException;
    }
}
