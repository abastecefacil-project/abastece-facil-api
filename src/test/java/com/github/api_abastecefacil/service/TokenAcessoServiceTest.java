package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.TokenInvalidoException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.TokenAcesso;
import com.github.api_abastecefacil.repository.TokenAcessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.TokenAcessoConstants.TOKEN_INVALIDO_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenAcessoServiceTest {

    private static final long ATIVACAO_HORAS = 48L;
    private static final long RECUPERACAO_HORAS = 1L;

    private static final String EMAIL = "colaborador@abastecefacil.com";
    private static final String IP = "10.0.0.7";

    @Mock
    private TokenAcessoRepository tokenAcessoRepository;

    private TokenAcessoService tokenAcessoService;

    @BeforeEach
    void setUp() {
        // @InjectMocks nao serve: o construtor recebe dois long de configuracao.
        tokenAcessoService = new TokenAcessoService(tokenAcessoRepository, ATIVACAO_HORAS, RECUPERACAO_HORAS);
    }

    @Test
    void gerarToken_ShouldPersistOnlySha256HashOfTheReturnedToken() {
        String tokenEmClaro = tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.ATIVACAO, IP);

        TokenAcesso salvo = capturarSalvo();

        assertThat(salvo.getTokenHash()).isEqualTo(sha256HexNoTeste(tokenEmClaro));
        assertThat(salvo.getTokenHash()).hasSize(64);
        assertThat(salvo.getTokenHash()).doesNotContain(tokenEmClaro);
        assertThat(salvo.getUsadoEm()).isNull();
    }

    @Test
    void gerarToken_ShouldReturnDistinctTokens_OnConsecutiveCalls() {
        String primeiro = tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.ATIVACAO, IP);
        String segundo = tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.ATIVACAO, IP);

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(primeiro).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void gerarToken_ShouldInvalidatePendingTokens_BeforeSavingTheNewOne() {
        tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.RECUPERACAO, IP);

        InOrder inOrder = inOrder(tokenAcessoRepository);
        inOrder.verify(tokenAcessoRepository).invalidarPendentes(EMAIL, "RECUPERACAO");
        inOrder.verify(tokenAcessoRepository).save(any(TokenAcesso.class));
    }

    @Test
    void gerarToken_ShouldApplyAtivacaoTtl_WhenFinalidadeIsAtivacao() {
        LocalDateTime antes = LocalDateTime.now();

        tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.ATIVACAO, IP);

        assertThat(capturarSalvo().getExpiraEm())
                .isBetween(antes.plusHours(ATIVACAO_HORAS), LocalDateTime.now().plusHours(ATIVACAO_HORAS));
    }

    @Test
    void gerarToken_ShouldApplyRecuperacaoTtl_WhenFinalidadeIsRecuperacao() {
        LocalDateTime antes = LocalDateTime.now();

        tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.RECUPERACAO, IP);

        assertThat(capturarSalvo().getExpiraEm())
                .isBetween(antes.plusHours(RECUPERACAO_HORAS), LocalDateTime.now().plusHours(RECUPERACAO_HORAS));
    }

    @Test
    void gerarToken_ShouldStoreRequesterIpAndFinalidade() {
        tokenAcessoService.gerarToken(EMAIL, FinalidadeToken.ATIVACAO, IP);

        TokenAcesso salvo = capturarSalvo();

        assertThat(salvo.getEmail()).isEqualTo(EMAIL);
        assertThat(salvo.getIpSolicitante()).isEqualTo(IP);
        assertThat(salvo.getFinalidade()).isEqualTo(FinalidadeToken.ATIVACAO);
    }

    @Test
    void validarEConsumir_ShouldReturnEmail_WhenExactlyOneRowIsAffected() {
        String tokenEmClaro = "token-em-claro";
        String tokenHash = sha256HexNoTeste(tokenEmClaro);

        when(tokenAcessoRepository.consumir(tokenHash, "ATIVACAO")).thenReturn(1);
        when(tokenAcessoRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(new TokenAcesso().setEmail(EMAIL)));

        String email = tokenAcessoService.validarEConsumir(tokenEmClaro, FinalidadeToken.ATIVACAO);

        assertThat(email).isEqualTo(EMAIL);
    }

    @Test
    void validarEConsumir_ShouldThrowTokenInvalido_WhenNoRowIsAffected() {
        // Cobre de uma vez token inexistente, ja usado e expirado: os tres caem no
        // mesmo predicado do UPDATE e devolvem 0.
        when(tokenAcessoRepository.consumir(anyString(), anyString())).thenReturn(0);

        assertThrows(TokenInvalidoException.class,
                () -> tokenAcessoService.validarEConsumir("qualquer", FinalidadeToken.ATIVACAO));

        verify(tokenAcessoRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void validarEConsumir_ShouldThrowTokenInvalido_WhenFinalidadeDiffers() {
        // A linha existe no banco, mas com a outra finalidade: o predicado
        // finalidade = :finalidade do UPDATE nao casa e o consumo afeta 0 linhas.
        // Vale nos dois sentidos -- ATIVACAO validado como RECUPERACAO e o inverso.
        String tokenDeAtivacao = "token-de-ativacao";
        String tokenDeRecuperacao = "token-de-recuperacao";

        when(tokenAcessoRepository.consumir(sha256HexNoTeste(tokenDeAtivacao), "RECUPERACAO")).thenReturn(0);
        when(tokenAcessoRepository.consumir(sha256HexNoTeste(tokenDeRecuperacao), "ATIVACAO")).thenReturn(0);

        assertThrows(TokenInvalidoException.class,
                () -> tokenAcessoService.validarEConsumir(tokenDeAtivacao, FinalidadeToken.RECUPERACAO));
        assertThrows(TokenInvalidoException.class,
                () -> tokenAcessoService.validarEConsumir(tokenDeRecuperacao, FinalidadeToken.ATIVACAO));

        // A finalidade chega ao repository como o nome do enum, nunca como ordinal.
        verify(tokenAcessoRepository).consumir(sha256HexNoTeste(tokenDeAtivacao), "RECUPERACAO");
        verify(tokenAcessoRepository).consumir(sha256HexNoTeste(tokenDeRecuperacao), "ATIVACAO");
        verify(tokenAcessoRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void validarEConsumir_ShouldUseTheSameGenericMessage_ForEveryRejection() {
        when(tokenAcessoRepository.consumir(anyString(), anyString())).thenReturn(0);

        TokenInvalidoException inexistente = assertThrows(TokenInvalidoException.class,
                () -> tokenAcessoService.validarEConsumir("nao-existe", FinalidadeToken.ATIVACAO));
        TokenInvalidoException finalidadeDivergente = assertThrows(TokenInvalidoException.class,
                () -> tokenAcessoService.validarEConsumir("nao-existe", FinalidadeToken.RECUPERACAO));

        assertThat(inexistente.getMessage()).isEqualTo(TOKEN_INVALIDO_MESSAGE);
        assertThat(finalidadeDivergente.getMessage()).isEqualTo(TOKEN_INVALIDO_MESSAGE);
    }

    @Test
    void validarEConsumir_ShouldPassTheHashAndNeverThePlainToken_ToTheRepository() {
        String tokenEmClaro = "segredo-que-nao-pode-vazar";

        when(tokenAcessoRepository.consumir(anyString(), anyString())).thenReturn(0);

        assertThrows(TokenInvalidoException.class,
                () -> tokenAcessoService.validarEConsumir(tokenEmClaro, FinalidadeToken.ATIVACAO));

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(tokenAcessoRepository).consumir(hashCaptor.capture(), eq("ATIVACAO"));

        assertThat(hashCaptor.getValue()).isEqualTo(sha256HexNoTeste(tokenEmClaro));
        assertThat(hashCaptor.getValue()).doesNotContain(tokenEmClaro);
    }

    @Test
    void emailDeTokenValido_ShouldReturnTheEmail_WithoutConsumingTheToken() {
        // A sonda do S3 nao pode queimar o link so porque a pessoa abriu a pagina.
        String tokenEmClaro = "token-da-sonda";
        when(tokenAcessoRepository.findEmailDeTokenValido(sha256HexNoTeste(tokenEmClaro), "ATIVACAO"))
                .thenReturn(Optional.of(EMAIL));

        assertThat(tokenAcessoService.emailDeTokenValido(tokenEmClaro, FinalidadeToken.ATIVACAO))
                .contains(EMAIL);

        verify(tokenAcessoRepository, never()).consumir(anyString(), anyString());
    }

    @Test
    void emailDeTokenValido_ShouldReturnEmpty_ForEveryRejection() {
        // Inexistente, expirado, ja usado e finalidade divergente caem no mesmo predicado
        // SQL e devolvem o mesmo vazio, sem distinguir qual ocorreu.
        when(tokenAcessoRepository.findEmailDeTokenValido(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThat(tokenAcessoService.emailDeTokenValido("qualquer", FinalidadeToken.ATIVACAO)).isEmpty();
        assertThat(tokenAcessoService.emailDeTokenValido("qualquer", FinalidadeToken.RECUPERACAO)).isEmpty();
    }

    @Test
    void emailDeTokenValido_ShouldPassTheHashAndTheFinalidadeName_ToTheRepository() {
        // Enum em query nativa e bindado por ordinal: a finalidade tem que chegar como
        // nome, igual nas escritas.
        String tokenEmClaro = "outro-token";
        when(tokenAcessoRepository.findEmailDeTokenValido(anyString(), anyString()))
                .thenReturn(Optional.empty());

        tokenAcessoService.emailDeTokenValido(tokenEmClaro, FinalidadeToken.RECUPERACAO);

        verify(tokenAcessoRepository)
                .findEmailDeTokenValido(sha256HexNoTeste(tokenEmClaro), "RECUPERACAO");
    }

    @Test
    void limparTokensExpirados_ShouldDeleteTokensExpiredForMoreThanSevenDays() {
        LocalDateTime antes = LocalDateTime.now();

        tokenAcessoService.limparTokensExpirados();

        ArgumentCaptor<LocalDateTime> limiteCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenAcessoRepository).deleteExpiradosAntesDe(limiteCaptor.capture());

        assertThat(limiteCaptor.getValue())
                .isBetween(antes.minusDays(7), LocalDateTime.now().minusDays(7));
    }

    private TokenAcesso capturarSalvo() {
        ArgumentCaptor<TokenAcesso> captor = ArgumentCaptor.forClass(TokenAcesso.class);
        verify(tokenAcessoRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    /**
     * Recalcula o hash de forma independente do service, para que o teste prove que o
     * que foi persistido e mesmo o SHA-256 do token devolvido -- e nao apenas que os
     * dois lados chamam o mesmo metodo.
     */
    private static String sha256HexNoTeste(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
