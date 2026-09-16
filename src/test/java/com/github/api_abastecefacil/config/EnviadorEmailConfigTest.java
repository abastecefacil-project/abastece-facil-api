package com.github.api_abastecefacil.config;

import com.github.api_abastecefacil.service.EnviadorEmail;
import com.github.api_abastecefacil.service.EnviadorEmailLog;
import com.github.api_abastecefacil.service.ResendEnviadorEmail;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnviadorEmailConfigTest {

    private static final String API_URL = "https://api.resend.com";
    private static final String API_KEY = "re_ChaveDeTeste_NaoUsarEmProducao";
    private static final String REMETENTE = "Abastece Fácil <onboarding@resend.dev>";

    private final EnviadorEmailConfig config = new EnviadorEmailConfig();

    // @InjectMocks nao serve: o metodo recebe quatro String de configuracao, e o proprio
    // ponto do teste e variar esses valores.
    private EnviadorEmail criar(String provedor, String apiKey, String remetente) {
        return config.enviadorEmail(RestClient.builder(), provedor, API_URL, apiKey, remetente);
    }

    @Test
    void enviadorEmail_ShouldReturnLogImplementation_WhenProvedorIsLog() {
        assertThat(criar("log", "", "")).isInstanceOf(EnviadorEmailLog.class);
    }

    @Test
    void enviadorEmail_ShouldReturnLogImplementation_WithoutAnySecretConfigured() {
        // Criterio de aceite: a aplicacao sobe sem chave, desde que o provedor seja log.
        assertThat(criar("log", null, null)).isInstanceOf(EnviadorEmailLog.class);
    }

    @Test
    void enviadorEmail_ShouldReturnResendImplementation_WhenProvedorIsResendWithKeyAndSender() {
        assertThat(criar("resend", API_KEY, REMETENTE)).isInstanceOf(ResendEnviadorEmail.class);
    }

    @Test
    void enviadorEmail_ShouldToleratePaddingAndCase_InTheProvedorValue() {
        // O valor costuma vir de variavel de ambiente escrita a mao.
        assertThat(criar("  LOG  ", "", "")).isInstanceOf(EnviadorEmailLog.class);
        assertThat(criar("Resend", API_KEY, REMETENTE)).isInstanceOf(ResendEnviadorEmail.class);
    }

    @Test
    void enviadorEmail_ShouldFail_WhenProvedorIsUnknown() {
        // Com @ConditionalOnProperty isso produziria zero beans e nao quebraria nada no
        // M3, porque ninguem injeta EnviadorEmail ainda -- o erro so apareceria no S2,
        // longe da causa.
        assertThatThrownBy(() -> criar("sendgrid", API_KEY, REMETENTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sendgrid")
                .hasMessageContaining("log")
                .hasMessageContaining("resend")
                .hasMessageContaining("ABASTECEFACIL_EMAIL_PROVEDOR");
    }

    @Test
    void enviadorEmail_ShouldReportTheRawValue_WhenProvedorIsUnknown() {
        // O valor cru, e nao o normalizado: senao a pessoa procuraria por algo que nao
        // digitou.
        assertThatThrownBy(() -> criar(" SendGrid ", API_KEY, REMETENTE))
                .hasMessageContaining(" SendGrid ");
    }

    @Test
    void enviadorEmail_ShouldFailNamingTheVariable_WhenResendHasNoApiKey() {
        assertThatThrownBy(() -> criar("resend", "", REMETENTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABASTECEFACIL_EMAIL_API_KEY");
    }

    @Test
    void enviadorEmail_ShouldFail_WhenResendApiKeyIsBlankOrNull() {
        assertThatThrownBy(() -> criar("resend", "   ", REMETENTE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> criar("resend", null, REMETENTE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enviadorEmail_ShouldNeverIncludeTheReceivedKey_InTheFailureMessage() {
        // O pior caso: alguem colou a senha, ou outra credencial, no lugar da chave.
        // Ecoa-la transformaria erro de configuracao em vazamento.
        String coladoPorEngano = "Senha-Real-Do-Admin-42";

        assertThatThrownBy(() -> criar("resend", coladoPorEngano, ""))
                .hasMessageNotContaining(coladoPorEngano);
    }

    @Test
    void enviadorEmail_ShouldFailNamingTheVariable_WhenResendHasNoSender() {
        assertThatThrownBy(() -> criar("resend", API_KEY, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABASTECEFACIL_EMAIL_REMETENTE");
    }
}
