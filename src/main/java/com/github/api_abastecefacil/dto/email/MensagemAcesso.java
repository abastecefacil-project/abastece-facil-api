package com.github.api_abastecefacil.dto.email;

import com.github.api_abastecefacil.model.FinalidadeToken;

/**
 * Tudo o que um e-mail de acesso precisa carregar.
 *
 * <p>É um record, e não cinco parâmetros soltos, por um motivo concreto:
 * {@code destinatario}, {@code nomeDestinatario} e {@code urlAcao} são três
 * {@link String} consecutivas com significados distintos, e o compilador não reclamaria
 * se duas fossem trocadas de lugar. Nomear cada campo na construção transforma um bug
 * silencioso — um convite endereçado ao nome em vez do e-mail — em erro de compilação.
 *
 * <p><b>{@code urlAcao} chega pronta.</b> O enviador não monta link e não conhece rota
 * de frontend: a estrutura das rotas de definir e redefinir senha é contrato do S6, e
 * fixá-la aqui acoplaria o canal de envio ao desenho de telas. Quem gera o token monta a
 * URL a partir de {@code abastecefacil.email.frontend-url} e a entrega montada.
 *
 * <p><b>{@code validadeHoras} é o prazo real do token</b>, não um valor decorativo. Vem
 * de {@code abastecefacil.token.ativacao-horas} ou {@code recuperacao-horas}, os mesmos
 * que o {@code TokenAcessoService} usou para calcular {@code expira_em}. O corpo do
 * e-mail exibe este número e nenhum outro — ver {@link ConteudoEmail}.
 */
public record MensagemAcesso(
        String destinatario,
        String nomeDestinatario,
        String urlAcao,
        FinalidadeToken finalidade,
        long validadeHoras
) {
}
