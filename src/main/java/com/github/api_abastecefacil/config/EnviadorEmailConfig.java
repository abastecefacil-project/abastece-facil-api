package com.github.api_abastecefacil.config;

import com.github.api_abastecefacil.service.EnviadorEmail;
import com.github.api_abastecefacil.service.EnviadorEmailLog;
import com.github.api_abastecefacil.service.ResendEnviadorEmail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Locale;

import static com.github.api_abastecefacil.constants.EmailConstants.*;

/**
 * Escolhe a implementação de {@link EnviadorEmail} por
 * {@code abastecefacil.email.provedor}.
 *
 * <p><b>Por que um {@code @Bean} com {@code switch} e não {@code @ConditionalOnProperty}.</b>
 * Com anotações condicionais, um valor inválido — {@code sendgrid}, ou só um erro de
 * digitação — não casaria com nenhuma condição e produziria <i>zero</i> beans. Nada
 * quebraria agora, porque no M3 ninguém injeta {@code EnviadorEmail} ainda; o erro
 * apareceria mais tarde, no S2, como uma {@code NoSuchBeanDefinitionException} sem
 * nenhuma relação aparente com a propriedade mal escrita. O {@code switch} falha na
 * subida, no ponto da causa, dizendo o valor recebido e os aceitos.
 *
 * <p>É o primeiro bean do projeto selecionado por propriedade. Lançar daqui derruba a
 * aplicação de propósito — ao contrário do {@code AdministradorInicialInitializer}, que
 * sobe mesmo com configuração ruim porque um {@code CommandLineRunner} que lança causaria
 * indisponibilidade. Aqui é o oposto: subir com o canal de e-mail mal configurado
 * significa descobrir o problema só quando o primeiro convite não chegar.
 */
@Configuration
public class EnviadorEmailConfig {

    /**
     * Os defaults no {@code @Value} repetem os do {@code application.yml} de propósito.
     * Nenhum teste do projeto sobe contexto, então um {@code @Value} mal escrito só
     * apareceria na subida real — o default evita que isso vire falha de inicialização.
     */
    @Bean
    public EnviadorEmail enviadorEmail(
            RestClient.Builder restClientBuilder,
            @Value("${abastecefacil.email.provedor:log}") String provedor,
            @Value("${abastecefacil.email.api-url:https://api.resend.com}") String apiUrl,
            @Value("${abastecefacil.email.api-key:}") String apiKey,
            @Value("${abastecefacil.email.remetente:}") String remetente) {

        return switch (normalizar(provedor)) {
            case PROVEDOR_LOG -> new EnviadorEmailLog();
            case PROVEDOR_RESEND -> criarResend(restClientBuilder, apiUrl, apiKey, remetente);
            default -> throw new IllegalStateException(String.format(
                    PROVEDOR_DESCONHECIDO_MESSAGE, provedor, PROVEDOR_LOG, PROVEDOR_RESEND));
        };
    }

    /**
     * O provedor real exige chave e remetente. Faltando qualquer um, a aplicação não
     * sobe: seguir adiante daria um adaptador que falha em toda chamada, e a primeira
     * notícia disso seria um usuário sem convite.
     *
     * <p>As duas mensagens nomeiam a variável que falta e <b>nunca incluem o valor
     * recebido</b> — mesma razão do {@code AdministradorInicialInitializer}: se alguém
     * colou a chave errada, ou colou outra coisa no lugar dela, o texto não pode ir para
     * o log.
     */
    private EnviadorEmail criarResend(
            RestClient.Builder restClientBuilder, String apiUrl, String apiKey, String remetente) {

        if (isBlank(apiKey)) {
            throw new IllegalStateException(CHAVE_AUSENTE_MESSAGE);
        }
        if (isBlank(remetente)) {
            throw new IllegalStateException(REMETENTE_AUSENTE_MESSAGE);
        }

        return new ResendEnviadorEmail(restClientBuilder, apiUrl, apiKey, remetente);
    }

    /**
     * Tolera espaço em volta e caixa alta, que é o que acontece quando o valor vem de
     * variável de ambiente escrita à mão. O valor cru, e não o normalizado, é o que
     * aparece na mensagem de erro — senão a pessoa procuraria por algo que não digitou.
     */
    private String normalizar(String provedor) {
        return provedor == null ? "" : provedor.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
