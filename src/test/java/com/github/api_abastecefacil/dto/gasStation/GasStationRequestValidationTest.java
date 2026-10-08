package com.github.api_abastecefacil.dto.gasStation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validação de latitude e longitude nos requests de posto.
 *
 * <p><b>Primeiro teste de Bean Validation do projeto.</b> Nenhum teste sobe contexto Spring,
 * então o {@code @Valid} do controller nunca roda na suíte; aqui o {@link Validator} é criado
 * direto, pelo mesmo Hibernate Validator que o Spring usaria. Prova as anotações, não o
 * {@code @Valid} — esse continua só verificável à mão.
 *
 * <p>Cada caso roda nos dois DTOs, porque as anotações estão duplicadas neles.
 */
class GasStationRequestValidationTest {

    private static final String JUNTAS = "Latitude e longitude devem ser informadas juntas";
    private static final String FAIXA_LATITUDE = "Latitude deve estar entre -90 e 90";
    private static final String FAIXA_LONGITUDE = "Longitude deve estar entre -180 e 180";

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void criarValidator() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fecharFabrica() {
        fabrica.close();
    }

    private static CreateGasStationRequest criacao(String latitude, String longitude) {
        return new CreateGasStationRequest("Posto", null, "12345678000199", "89201-250", "Centro", "Rua A, 1",
                "SC", "Joinville", null, null, decimal(latitude), decimal(longitude));
    }

    private static UpdateGasStationRequest edicao(String latitude, String longitude) {
        return new UpdateGasStationRequest("Posto", null, "12345678000199", "89201-250", "Centro", "Rua A, 1",
                "SC", "Joinville", true, null, null, decimal(latitude), decimal(longitude));
    }

    private static BigDecimal decimal(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }

    /** As mensagens das violações dos dois DTOs, que precisam coincidir. */
    private static Set<String> mensagens(String latitude, String longitude) {
        Set<String> criacao = mensagensDe(validator.validate(criacao(latitude, longitude)));
        Set<String> edicao = mensagensDe(validator.validate(edicao(latitude, longitude)));
        assertThat(edicao).isEqualTo(criacao);
        return criacao;
    }

    private static <T> Set<String> mensagensDe(Set<ConstraintViolation<T>> violacoes) {
        return violacoes.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void shouldAccept_WhenNeitherCoordinateIsInformed() {
        assertThat(mensagens(null, null)).isEmpty();
    }

    @Test
    void shouldAccept_WhenBothCoordinatesAreInformedWithinRange() {
        assertThat(mensagens("-26.30451099", "-48.84871235")).isEmpty();
    }

    @Test
    void shouldAccept_TheExactLimits() {
        assertThat(mensagens("90", "180")).isEmpty();
        assertThat(mensagens("-90", "-180")).isEmpty();
    }

    @Test
    void shouldReject_LatitudeWithoutLongitude() {
        assertThat(mensagens("-26.3", null)).containsExactly(JUNTAS);
    }

    @Test
    void shouldReject_LongitudeWithoutLatitude() {
        assertThat(mensagens(null, "-48.8")).containsExactly(JUNTAS);
    }

    @Test
    void shouldReject_LatitudeOutOfRange() {
        assertThat(mensagens("90.0001", "-48.8")).containsExactly(FAIXA_LATITUDE);
        assertThat(mensagens("-90.0001", "-48.8")).containsExactly(FAIXA_LATITUDE);
    }

    @Test
    void shouldReject_LongitudeOutOfRange() {
        assertThat(mensagens("-26.3", "180.0001")).containsExactly(FAIXA_LONGITUDE);
        assertThat(mensagens("-26.3", "-180.0001")).containsExactly(FAIXA_LONGITUDE);
    }

    @Test
    void shouldReportTheViolationAsAProperty_SoItReachesTheHandlerAsAFieldError() {
        Set<ConstraintViolation<CreateGasStationRequest>> violacoes = validator.validate(criacao("-26.3", null));

        assertThat(violacoes).singleElement()
                .satisfies(violacao -> assertThat(violacao.getPropertyPath().toString())
                        .isEqualTo("coordenadasCompletas"));
    }
}
