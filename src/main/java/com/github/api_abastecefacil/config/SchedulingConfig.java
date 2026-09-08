package com.github.api_abastecefacil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Habilita a execução fora da thread de requisição — {@code @Scheduled} e {@code @Async} —
 * e publica o relógio que torna as duas testáveis.
 *
 * <p>O agendamento veio primeiro, no M2, por causa da limpeza periódica de
 * {@code tokens_acesso} ({@code TokenAcessoService.limparTokensExpirados}). O S4
 * acrescentou a limpeza do {@code RateLimitService} e, com o envio assíncrono da
 * recuperação de senha, o {@code @EnableAsync}.
 *
 * <p><b>A armadilha é a mesma nas duas anotações, e é silenciosa:</b> sem a habilitação,
 * {@code @Scheduled} nunca dispara e {@code @Async} roda <b>síncrono, na thread da
 * requisição</b>. Em nenhum dos dois casos há erro, aviso ou falha de subida — a aplicação
 * sobe normalmente e o comportamento simplesmente não é o que o código aparenta. No caso
 * do {@code @Async} isso custaria a propriedade que o S4 existe para garantir: a resposta
 * de {@code POST /api/auth/recuperacao} passaria a esperar pela consulta ao banco e pela
 * chamada ao provedor de e-mail, e o tempo de resposta voltaria a revelar se a conta
 * existe.
 *
 * <p>As duas moram na mesma classe porque são a mesma decisão — "isto não roda na thread
 * de quem pediu" — e duas classes de configuração de uma linha cada só dispersariam o
 * assunto. O executor é o {@code applicationTaskExecutor} padrão do Spring Boot: não há
 * fila, pool customizado nem mensageria, que seriam infraestrutura demais para um envio
 * de e-mail.
 *
 * <p>Nenhuma dependência nova: {@code @Scheduled}, {@code @Async} e {@link Clock} vêm do
 * {@code spring-context} e do JDK, ambos já presentes.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    /**
     * Relógio da aplicação, injetável em quem precisa medir passagem de tempo.
     *
     * <p>Existe para o {@code RateLimitService}: com {@code Instant.now()} escrito direto
     * no serviço, provar que a janela de quinze minutos desliza exigiria um teste que
     * dorme quinze minutos. Com o relógio como dependência, o teste substitui por um
     * relógio que ele controla.
     *
     * <p>{@code systemDefaultZone} e não {@code systemUTC} para acompanhar o resto do
     * projeto, que grava {@code LocalDateTime.now()} no fuso da JVM. Aqui a escolha é
     * inócua — só se usa o {@link java.time.Instant}, que não tem fuso —, mas um relógio
     * em UTC ao lado de escritas em hora local convidaria à confusão que a §9 do CLAUDE.md
     * já documenta sobre {@code tokens_acesso}.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
