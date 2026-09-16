package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.email.ConteudoEmail;
import com.github.api_abastecefacil.dto.email.MensagemAcesso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.api_abastecefacil.constants.EmailConstants.ENVIO_SIMULADO_MESSAGE;

/**
 * Implementação de desenvolvimento: escreve o link no log e não envia nada.
 *
 * <p>É o provedor <b>default</b> para que rodar o projeto localmente não dependa de rede,
 * não exija chave de API e não consuma cota do Resend. Quem clona o repositório sobe a
 * aplicação e vê o link no console.
 *
 * <p><b>Esta é a única classe do projeto autorizada a registrar a URL com o token em
 * claro.</b> A regra do M2 continua valendo em todo o resto: o token em claro não é
 * persistido, não entra em DTO, não aparece em mensagem de exceção e não é logado. Aqui
 * ele é logado porque o log <i>é</i> o canal de entrega — sem isso a implementação não
 * teria função.
 *
 * <p>A consequência disso é que <b>{@code provedor: log} em produção equivale a publicar
 * tokens de acesso no log da aplicação</b>: qualquer pessoa com acesso ao log poderia
 * definir a senha de qualquer usuário convidado. Por isso
 * {@code ABASTECEFACIL_EMAIL_PROVEDOR} está marcado como obrigatório em produção na §3
 * do CLAUDE.md, com o valor {@code resend}.
 */
public class EnviadorEmailLog implements EnviadorEmail {

    private static final Logger log = LoggerFactory.getLogger(EnviadorEmailLog.class);

    @Override
    public void enviar(MensagemAcesso mensagem) {
        ConteudoEmail conteudo = ConteudoEmail.de(mensagem);

        // O corpo inteiro nao vai para o log de proposito: sao dezenas de linhas de HTML
        // que afogariam o console. O que interessa em desenvolvimento e o link, e o
        // assunto para conferir que a finalidade certa foi escolhida.
        log.info(
                ENVIO_SIMULADO_MESSAGE,
                mensagem.destinatario(),
                mensagem.finalidade(),
                conteudo.assunto(),
                mensagem.validadeHoras(),
                mensagem.urlAcao()
        );
    }
}
