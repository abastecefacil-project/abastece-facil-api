package com.github.api_abastecefacil.constants;

public final class TokenAcessoConstants {

    private TokenAcessoConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    // Mensagem unica para as quatro rejeicoes possiveis -- token inexistente, ja
    // usado, expirado e de finalidade divergente. A uniformidade e deliberada:
    // mensagens distintas revelariam a quem tem o token se ele existe, se ja foi
    // consumido ou para que servia.
    public static final String TOKEN_INVALIDO_MESSAGE = "Token inválido ou expirado";

    public static final int TOKEN_BYTES = 32;

    public static final String ALGORITMO_HASH = "SHA-256";

    public static final int DIAS_RETENCAO_EXPIRADOS = 7;

    // Diariamente as 3h, fora do horario de uso do sistema.
    public static final String CRON_LIMPEZA = "0 0 3 * * *";
}
