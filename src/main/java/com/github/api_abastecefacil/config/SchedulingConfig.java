package com.github.api_abastecefacil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o agendamento por {@code @Scheduled} na aplicação.
 *
 * <p>É a primeira e única habilitação de agendamento do projeto, e existe por causa da
 * limpeza periódica de {@code tokens_acesso}
 * ({@code TokenAcessoService.limparTokensExpirados}). Sem esta classe a anotação é
 * ignorada em silêncio — a aplicação sobe normalmente e a tarefa simplesmente nunca
 * roda.
 *
 * <p>Fica aqui, e não em {@code Application}, para acompanhar a convenção do pacote
 * {@code config}, onde já mora {@code @EnableWebSecurity}. Nenhuma dependência nova:
 * {@code @Scheduled} vem do {@code spring-context}, já presente.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
