package com.github.api_abastecefacil.dto.email;

import static com.github.api_abastecefacil.constants.EmailConstants.*;

/**
 * Assunto e corpo de um e-mail de acesso, já renderizados.
 *
 * <p>É compartilhado pelas duas implementações de {@code EnviadorEmail} de propósito: o
 * texto que o usuário lê não pode variar com o provedor de envio, e concentrá-lo num
 * ponto só torna as regras de conteúdo verificáveis sem tocar em HTTP.
 *
 * <p>A interpolação é {@link String#format} e nada mais. Não há Thymeleaf nem motor de
 * template no projeto, e dois textos por finalidade não justificam introduzir um.
 */
public record ConteudoEmail(String assunto, String corpoHtml, String corpoTexto) {

    /**
     * Monta o conteúdo a partir da mensagem, seguindo o precedente de
     * {@code ErrorResponse.of(...)}: um record com factory estática.
     *
     * <p>O {@code switch} é uma switch expression sem {@code default}, como
     * {@code TokenAcessoService.resolverHoras}. Uma finalidade nova passa a quebrar a
     * compilação aqui, em vez de cair num caso silencioso e mandar o texto errado.
     */
    public static ConteudoEmail de(MensagemAcesso mensagem) {
        String validade = formatarValidade(mensagem.validadeHoras());

        return switch (mensagem.finalidade()) {
            case ATIVACAO -> new ConteudoEmail(
                    ASSUNTO_ATIVACAO,
                    renderizar(HTML_ATIVACAO, mensagem, validade),
                    renderizar(TEXTO_ATIVACAO, mensagem, validade)
            );
            case RECUPERACAO -> new ConteudoEmail(
                    ASSUNTO_RECUPERACAO,
                    renderizar(HTML_RECUPERACAO, mensagem, validade),
                    renderizar(TEXTO_RECUPERACAO, mensagem, validade)
            );
        };
    }

    /**
     * A URL entra duas vezes: no destino do link e em texto visível, para quem lê o
     * e-mail num cliente que não renderiza HTML ou não deixa clicar.
     */
    private static String renderizar(String template, MensagemAcesso mensagem, String validade) {
        return String.format(
                template,
                mensagem.nomeDestinatario(),
                mensagem.urlAcao(),
                mensagem.urlAcao(),
                validade
        );
    }

    /**
     * O prazo exibido vem sempre do parâmetro, nunca de constante — os TTLs são
     * configuráveis desde o M2 e o texto não pode divergir do prazo com que o token foi
     * realmente emitido. O caso de 1 hora existe só para não escrever "1 horas": a
     * recuperação usa exatamente esse prazo por padrão.
     */
    private static String formatarValidade(long horas) {
        return horas == 1 ? VALIDADE_UMA_HORA : String.format(VALIDADE_HORAS, horas);
    }
}
