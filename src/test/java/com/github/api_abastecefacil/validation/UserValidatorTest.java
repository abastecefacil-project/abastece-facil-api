package com.github.api_abastecefacil.validation;

import com.github.api_abastecefacil.exception.DominioEmailNaoPermitidoException;
import com.github.api_abastecefacil.exception.InvalidUserDataException;
import com.github.api_abastecefacil.exception.SenhaFracaException;
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

    // ---------- validarSenha ----------

    private static final String EMAIL = "joao.silva@fiesc.org.br";
    private static final String NOME = "Joao Silva";

    @Test
    void validarSenha_ShouldAccept_AtTenCharactersWithLetterAndDigit() {
        assertThatCode(() -> UserValidator.validarSenha("Chuva8Verde", EMAIL, NOME))
                .doesNotThrowAnyException();
    }

    @Test
    void validarSenha_ShouldThrow_AtNineCharacters() {
        // O criterio de aceite: 9 e rejeitado no backend mesmo que o frontend deixasse
        // passar -- a requisicao chega de curl sem passar por formulario nenhum.
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("Chuva8Ver", EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldThrow_WhenThereIsNoDigit() {
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("ChuvaVerdeSemNumero", EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldThrow_WhenThereIsNoLetter() {
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("1234567890", EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldThrow_WhenNull() {
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha(null, EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldThrow_WhenItContainsAWordOfTheName() {
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("silva2024xyz", EMAIL, NOME));
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("XXjoao2024xx", EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldBeCaseInsensitive_OnPersonalData() {
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("SILVA2024xyz", EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldThrow_WhenItContainsTheEmailOrItsLocalPart() {
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("x" + EMAIL + "1", EMAIL, NOME));
        assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("joao.silva9x", EMAIL, NOME));
    }

    @Test
    void validarSenha_ShouldIgnoreNameWordsShorterThanFourCharacters() {
        // O ponto do limiar: com corte em 3, "Ana" reprovaria banana123456, que nao tem
        // nada de pessoal.
        assertThatCode(() -> UserValidator.validarSenha("banana123456", "ana@fiesc.org.br", "Ana Luz"))
                .doesNotThrowAnyException();
    }

    @Test
    void validarSenha_ShouldNotEchoThePassword_InTheErrorMessage() {
        // Mesmo cuidado do A3 com o hash e do M3 com a chave: nem no caminho de rejeicao
        // a senha recebida pode aparecer.
        String senha = "silva2024secreta";

        SenhaFracaException ex = assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha(senha, EMAIL, NOME));

        assertThat(ex.getMessage()).doesNotContain(senha);
    }

    @Test
    void validarSenha_ShouldNotRevealWhichTermMatched() {
        // A mensagem diz o motivo sem virar manual de como contornar a regra: nao lista a
        // palavra detectada nem o comprimento minimo do criterio.
        SenhaFracaException ex = assertThrows(SenhaFracaException.class,
                () -> UserValidator.validarSenha("silva2024xyz", EMAIL, NOME));

        assertThat(ex.getMessage()).doesNotContain("silva").doesNotContain("Silva");
    }

    @Test
    void validarSenha_ShouldAccept_WhenNameAndEmailAreNull() {
        // Caminho defensivo: o metodo e publico e estatico, e pode ser chamado de onde os
        // dados do dono nao estejam disponiveis.
        assertThatCode(() -> UserValidator.validarSenha("Chuva8Verde", null, null))
                .doesNotThrowAnyException();
    }

    // ---------- validarDominioEmail ----------

    private static final java.util.List<String> DOMINIOS =
            java.util.List.of("fiesc.org.br", "sesisenai.org.br");

    @Test
    void validarDominioEmail_ShouldAccept_OnExactDomainMatch() {
        assertThatCode(() -> {
            UserValidator.validarDominioEmail("joao@fiesc.org.br", DOMINIOS);
            UserValidator.validarDominioEmail("maria@sesisenai.org.br", DOMINIOS);
        }).doesNotThrowAnyException();
    }

    @Test
    void validarDominioEmail_ShouldAccept_OnSubdomain() {
        assertThatCode(() -> {
            UserValidator.validarDominioEmail("joao@sc.fiesc.org.br", DOMINIOS);
            UserValidator.validarDominioEmail("joao@joinville.sesisenai.org.br", DOMINIOS);
        }).doesNotThrowAnyException();
    }

    @Test
    void validarDominioEmail_ShouldBeCaseInsensitive() {
        assertThatCode(() -> UserValidator.validarDominioEmail("Joao@FIESC.ORG.BR", DOMINIOS))
                .doesNotThrowAnyException();
    }

    @Test
    void validarDominioEmail_ShouldReject_WhenAllowedDomainIsTheLocalPart() {
        // O ataque classico: um contains sobre o e-mail inteiro aceitaria isto, e o
        // endereco e do Gmail. A comparacao tem que ser sobre o que vem depois do ULTIMO
        // arroba.
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("fiesc.org.br@gmail.com", DOMINIOS));
    }

    @Test
    void validarDominioEmail_ShouldReject_WhenAllowedDomainIsALabelOfAnotherDomain() {
        // O dominio real aqui e exemplo.com; fiesc.org.br e so um rotulo no meio.
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("contato@fiesc.org.br.exemplo.com", DOMINIOS));
    }

    @Test
    void validarDominioEmail_ShouldReject_WhenDomainMerelyEndsWithAllowedOne() {
        // Sem o ponto em "." + permitido, notfiesc.org.br terminaria em fiesc.org.br e
        // passaria.
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("joao@notfiesc.org.br", DOMINIOS));
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("joao@xsesisenai.org.br", DOMINIOS));
    }

    @Test
    void validarDominioEmail_ShouldReject_WhenDomainIsNotAllowed() {
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("fulano@gmail.com", DOMINIOS));
    }

    @Test
    void validarDominioEmail_ShouldReject_WhenThereIsNoDomain() {
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("sem-arroba", DOMINIOS));
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("termina-em@", DOMINIOS));
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail(null, DOMINIOS));
    }

    @Test
    void validarDominioEmail_ShouldRejectEverything_WhenTheAllowListIsEmpty() {
        // Deny by default: lista vazia ou nula nao autoriza ninguem.
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("joao@fiesc.org.br", java.util.List.of()));
        assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("joao@fiesc.org.br", null));
    }

    @Test
    void validarDominioEmail_ShouldListTheAllowedDomains_InTheErrorMessage() {
        // Quem digitou foi o gestor, no formulario, e o caso provavel e erro de digitacao.
        DominioEmailNaoPermitidoException ex = assertThrows(DominioEmailNaoPermitidoException.class,
                () -> UserValidator.validarDominioEmail("fulano@gmail.com", DOMINIOS));

        assertThat(ex.getMessage()).contains("fiesc.org.br").contains("sesisenai.org.br");
    }

    @Test
    void validarDominioEmail_ShouldToleratePaddingInTheConfiguredList() {
        // A lista vem de CSV em configuracao, entao espaco em volta e comum.
        assertThatCode(() -> UserValidator.validarDominioEmail(
                "joao@fiesc.org.br", java.util.List.of(" fiesc.org.br ", "  "))).doesNotThrowAnyException();
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
