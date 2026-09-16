package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.exception.EnvioEmailException;

/**
 * Canal de envio dos e-mails de acesso.
 *
 * <p>É a primeira interface de serviço do projeto com mais de uma implementação. A
 * escolha é feita na subida, por {@code abastecefacil.email.provedor}, em
 * {@code EnviadorEmailConfig} — nenhum chamador sabe qual implementação recebeu, e é
 * exatamente esse o ponto: trocar de provedor não deve tocar em quem envia.
 *
 * <p>Implementações não geram token, não montam link e não decidem prazo. Tudo isso
 * chega pronto em {@link MensagemAcesso}. O que elas fazem é transformar a mensagem em
 * assunto e corpo e entregá-la — ou falhar de um jeito tratável.
 */
public interface EnviadorEmail {

    /**
     * Envia a mensagem.
     *
     * @throws EnvioEmailException quando o provedor rejeita a mensagem ou não pode ser
     *                             alcançado. Nenhuma falha de transporte escapa crua:
     *                             o {@code GlobalExceptionHandler} traduz esta exceção
     *                             em 502 com {@code error = "EMAIL_NAO_ENVIADO"}.
     */
    void enviar(MensagemAcesso mensagem);
}
