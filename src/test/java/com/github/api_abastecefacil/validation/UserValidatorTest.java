package com.github.api_abastecefacil.validation;

import com.github.api_abastecefacil.exception.InvalidUserDataException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserValidatorTest {

    // ---------- validarMatricula ----------

    @Test
    void validarMatricula_ShouldAccept_WhenNull() {
        assertThatCode(() -> UserValidator.validarMatricula(null)).doesNotThrowAnyException();
    }

    @Test
    void validarMatricula_ShouldAccept_AtLowerBoundOfFourDigits() {
        assertThatCode(() -> UserValidator.validarMatricula("1234")).doesNotThrowAnyException();
    }

    @Test
    void validarMatricula_ShouldAccept_AtUpperBoundOfTwelveDigits() {
        assertThatCode(() -> UserValidator.validarMatricula("123456789012")).doesNotThrowAnyException();
    }

    @Test
    void validarMatricula_ShouldThrow_WhenBelowLowerBound() {
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarMatricula("123"));
    }

    @Test
    void validarMatricula_ShouldThrow_WhenAboveUpperBound() {
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarMatricula("1234567890123"));
    }

    @Test
    void validarMatricula_ShouldThrow_WhenNotOnlyDigits() {
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarMatricula("12A4"));
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarMatricula("123 4"));
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarMatricula(""));
    }

    // ---------- validarTelefone ----------

    @Test
    void validarTelefone_ShouldAccept_WhenNull() {
        assertThatCode(() -> UserValidator.validarTelefone(null)).doesNotThrowAnyException();
    }

    @Test
    void validarTelefone_ShouldAccept_WithMask() {
        assertThatCode(() -> {
            UserValidator.validarTelefone("(47) 99999-8888");
            UserValidator.validarTelefone("(47)99999-8888");
            UserValidator.validarTelefone("(47) 3422-1234");
            UserValidator.validarTelefone("47 99999-8888");
        }).doesNotThrowAnyException();
    }

    @Test
    void validarTelefone_ShouldAccept_WithoutMask() {
        assertThatCode(() -> {
            UserValidator.validarTelefone("47999998888");
            UserValidator.validarTelefone("4734221234");
        }).doesNotThrowAnyException();
    }

    @Test
    void validarTelefone_ShouldThrow_WhenInvalid() {
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarTelefone("999"));
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarTelefone("abc"));
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarTelefone("479999988887"));
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarTelefone(""));
    }

    @Test
    void validarTelefone_ShouldThrow_WhenDdiIsPresent() {
        // Escopo deliberado: DDI nao e aceito. Ver o javadoc de UserValidator.
        assertThrows(InvalidUserDataException.class, () -> UserValidator.validarTelefone("+55 47 99999-8888"));
    }

    // ---------- normalizarTelefone ----------

    @Test
    void normalizarTelefone_ShouldStripMask() {
        assertThat(UserValidator.normalizarTelefone("(47) 99999-8888")).isEqualTo("47999998888");
        assertThat(UserValidator.normalizarTelefone("(47) 3422-1234")).isEqualTo("4734221234");
    }

    @Test
    void normalizarTelefone_ShouldReturnNull_WhenNullOrWithoutDigits() {
        assertThat(UserValidator.normalizarTelefone(null)).isNull();
        assertThat(UserValidator.normalizarTelefone("")).isNull();
        assertThat(UserValidator.normalizarTelefone("   ")).isNull();
        assertThat(UserValidator.normalizarTelefone("(  ) -")).isNull();
    }

    @Test
    void normalizarTelefone_ShouldBeIdempotent() {
        String umaVez = UserValidator.normalizarTelefone("(47) 99999-8888");

        assertThat(UserValidator.normalizarTelefone(umaVez)).isEqualTo(umaVez);
    }
}
