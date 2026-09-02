package com.github.api_abastecefacil.dto.email;

import java.util.List;

/**
 * Corpo do {@code POST /emails} da API do Resend.
 *
 * <p>Os nomes dos componentes são os nomes dos campos JSON que o Resend espera, para o
 * Jackson serializar sem nenhuma anotação. Estão em inglês por serem contrato de um
 * serviço externo — a convenção de português vale para o domínio do projeto, não para o
 * payload de terceiro.
 *
 * <p>É o primeiro DTO do projeto que modela um payload de <b>saída</b>: os demais são de
 * entrada ({@code Create...Request}) ou de resposta ({@code ...Response}).
 *
 * <p>{@code html} e {@code text} viajam juntos na mesma requisição de propósito. O
 * Resend usa o texto puro como alternativa para clientes que não renderizam HTML, e a
 * presença das duas partes também reduz a chance de a mensagem ser classificada como
 * spam.
 */
public record ResendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String html,
        String text
) {

    public static ResendEmailRequest de(String remetente, String destinatario, ConteudoEmail conteudo) {
        return new ResendEmailRequest(
                remetente,
                List.of(destinatario),
                conteudo.assunto(),
                conteudo.corpoHtml(),
                conteudo.corpoTexto()
        );
    }
}
