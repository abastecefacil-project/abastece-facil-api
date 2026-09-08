package com.github.api_abastecefacil.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extração do IP de quem fez a requisição, gravado em
 * {@code tokens_acesso.ip_solicitante} para auditoria.
 *
 * <p><b>Ponto único de propósito, e o motivo de esta classe existir.</b>
 * {@code getRemoteAddr()} devolve o endereço do proxy quando a aplicação roda atrás de
 * load balancer ou reverse proxy — em produção, isso faria a auditoria registrar sempre o
 * mesmo IP. O endereço real chegaria em {@code X-Forwarded-For}.
 *
 * <p>O parsing desse header <b>não</b> está implementado, e isso é deliberado: confiar no
 * {@code X-Forwarded-For} sem saber se existe um proxy confiável na frente é pior que não
 * ter auditoria nenhuma, porque qualquer cliente pode forjá-lo e o registro passaria a ser
 * uma mentira assinada. A correção vem quando o deploy definir a topologia — e é aqui,
 * num lugar só.
 *
 * <p>Era um método privado do {@code UserController} até o S4. Quando o {@code AuthController}
 * passou a precisar do mesmo IP para a recuperação de senha, copiá-lo teria criado o
 * segundo lugar a corrigir — exatamente o que a promessa acima diz que não existe.
 */
public final class IpSolicitante {

    private IpSolicitante() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static String extrair(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
