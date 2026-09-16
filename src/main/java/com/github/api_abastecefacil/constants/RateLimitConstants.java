package com.github.api_abastecefacil.constants;

import java.time.Duration;

/**
 * Política de limite de solicitações da recuperação de senha (S4).
 *
 * <p><b>Constantes, e não propriedades de configuração</b>, ao contrário de
 * {@code abastecefacil.token.*}. Os TTLs viraram configuráveis porque o <b>corpo do
 * e-mail exibe o prazo</b> e não pode divergir dele — uma constante ali passaria a mentir
 * assim que alguém mudasse a propriedade. O limite de requisições não tem acoplamento
 * equivalente: nada além do próprio limitador o lê, e nenhum texto o repete. Vira
 * propriedade no dia em que houver necessidade real de ajustá-lo sem novo deploy.
 */
public final class RateLimitConstants {

    private RateLimitConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    /**
     * Prefixos de chave. Existem para que os dois espaços de nomes não se misturem: sem
     * eles, um IP que por acaso fosse igual a um e-mail compartilharia contador — hoje
     * impossível, mas a separação custa nada e sobrevive a chaves futuras.
     */
    public static final String CHAVE_RECUPERACAO_EMAIL = "recuperacao:email:";

    public static final String CHAVE_RECUPERACAO_IP = "recuperacao:ip:";

    /** Três solicitações por e-mail a cada quinze minutos. */
    public static final int LIMITE_RECUPERACAO_EMAIL = 3;

    public static final Duration JANELA_RECUPERACAO_EMAIL = Duration.ofMinutes(15);

    /**
     * Dez por IP a cada hora. É mais frouxo que o limite por e-mail de propósito: uma
     * rede corporativa com NAT apresenta um único endereço para muitos colaboradores, e
     * um teto apertado por IP transformaria o limite numa negação de serviço para o
     * escritório inteiro.
     */
    public static final int LIMITE_RECUPERACAO_IP = 10;

    public static final Duration JANELA_RECUPERACAO_IP = Duration.ofHours(1);

    /**
     * A maior das janelas. Um registro mais velho que isto não influencia decisão
     * nenhuma, então é o critério de descarte da limpeza periódica.
     */
    public static final Duration JANELA_MAXIMA = JANELA_RECUPERACAO_IP;

    /**
     * De hora em hora, aos cinco minutos. Não coincide com a limpeza de
     * {@code tokens_acesso} (3h da manhã) porque aqui não há motivo para esperar a
     * madrugada: o estado é de memória, a varredura é sobre um mapa pequeno, e adiá-la
     * por um dia inteiro deixaria acumular chaves de um dia inteiro.
     */
    public static final String CRON_LIMPEZA_RATE_LIMIT = "0 5 * * * *";

    public static final String LIMPEZA_LOG =
            "Limpeza do limitador de solicitações: {} chave(s) sem registro recente removida(s)";
}
