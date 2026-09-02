package com.github.api_abastecefacil.constants;

public final class EmailConstants {

    private EmailConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    // ---------------------------------------------------------------- provedores

    public static final String PROVEDOR_LOG = "log";

    public static final String PROVEDOR_RESEND = "resend";

    /** Caminho do envio na API do Resend, relativo a abastecefacil.email.api-url. */
    public static final String RESEND_PATH_EMAILS = "/emails";

    // ------------------------------------------------------------------ assuntos

    public static final String ASSUNTO_ATIVACAO =
            "Abastece Fácil — defina a senha do seu primeiro acesso";

    public static final String ASSUNTO_RECUPERACAO =
            "Abastece Fácil — redefinição de senha";

    // ----------------------------------------------------------------- templates
    //
    // Interpolados com String.format na ordem (nome, url, url, validade). NAO use '%'
    // literal nestes textos: String.format o interpretaria como conversao. Se algum dia
    // for preciso, escreva '%%'.
    //
    // O prazo NUNCA aparece como constante aqui -- ele chega formatado no ultimo
    // argumento, vindo de MensagemAcesso.validadeHoras(). Os TTLs sao configuraveis
    // desde o M2 (abastecefacil.token.*) e o texto do e-mail nao pode divergir do prazo
    // real com que o token foi emitido.

    public static final String HTML_ATIVACAO = """
            <div style="font-family: Arial, Helvetica, sans-serif; color: #1F2933; line-height: 1.6;">
              <h2 style="color: #16496E; margin-bottom: 16px;">Bem-vindo(a) ao Abastece Fácil</h2>
              <p>Olá, %s.</p>
              <p>Uma conta foi criada para você no Abastece Fácil. Para concluir o primeiro
                 acesso, defina a sua senha no link abaixo:</p>
              <p style="margin: 24px 0;">
                <a href="%s" style="background-color: #16496E; color: #FFFFFF; padding: 12px 20px;
                   border-radius: 10px; text-decoration: none; display: inline-block;">Definir minha senha</a>
              </p>
              <p>Se o botão não funcionar, copie e cole este endereço no navegador:<br>
                 <span style="color: #6B7785; word-break: break-all;">%s</span></p>
              <p><strong>Este link vale por %s e pode ser usado uma única vez.</strong></p>
              <p style="color: #6B7785;">Se você não esperava este convite, ignore esta mensagem —
                 nenhuma ação será tomada.</p>
            </div>
            """;

    public static final String TEXTO_ATIVACAO = """
            Bem-vindo(a) ao Abastece Fácil

            Olá, %s.

            Uma conta foi criada para você no Abastece Fácil. Para concluir o primeiro acesso,
            defina a sua senha no endereço abaixo:

            %s

            Se preferir, copie e cole o endereço acima no navegador: %s

            Este link vale por %s e pode ser usado uma única vez.

            Se você não esperava este convite, ignore esta mensagem — nenhuma ação será tomada.
            """;

    public static final String HTML_RECUPERACAO = """
            <div style="font-family: Arial, Helvetica, sans-serif; color: #1F2933; line-height: 1.6;">
              <h2 style="color: #16496E; margin-bottom: 16px;">Redefinição de senha</h2>
              <p>Olá, %s.</p>
              <p>Recebemos um pedido para redefinir a senha da sua conta no Abastece Fácil.
                 Para escolher uma nova senha, use o link abaixo:</p>
              <p style="margin: 24px 0;">
                <a href="%s" style="background-color: #16496E; color: #FFFFFF; padding: 12px 20px;
                   border-radius: 10px; text-decoration: none; display: inline-block;">Redefinir minha senha</a>
              </p>
              <p>Se o botão não funcionar, copie e cole este endereço no navegador:<br>
                 <span style="color: #6B7785; word-break: break-all;">%s</span></p>
              <p><strong>Este link vale por %s e pode ser usado uma única vez.</strong></p>
              <p style="color: #6B7785;">Se você não solicitou a redefinição, ignore esta mensagem —
                 a sua senha atual continua valendo.</p>
            </div>
            """;

    public static final String TEXTO_RECUPERACAO = """
            Redefinição de senha

            Olá, %s.

            Recebemos um pedido para redefinir a senha da sua conta no Abastece Fácil. Para
            escolher uma nova senha, use o endereço abaixo:

            %s

            Se preferir, copie e cole o endereço acima no navegador: %s

            Este link vale por %s e pode ser usado uma única vez.

            Se você não solicitou a redefinição, ignore esta mensagem — a sua senha atual
            continua valendo.
            """;

    public static final String VALIDADE_UMA_HORA = "1 hora";

    public static final String VALIDADE_HORAS = "%d horas";

    // ---------------------------------------------------------- mensagens de log

    /**
     * Única linha de log do projeto que registra a URL com o token em claro. Existe
     * apenas para desenvolvimento — ver o javadoc de {@code EnviadorEmailLog}.
     */
    public static final String ENVIO_SIMULADO_MESSAGE =
            "[E-MAIL SIMULADO] Nenhuma mensagem foi enviada. destinatario={} finalidade={} "
                    + "assunto=\"{}\" validade={} link={}";

    public static final String RESEND_ENVIADO_MESSAGE =
            "E-mail enviado pelo Resend: destinatario={} finalidade={} id={}";

    /**
     * Sem o corpo da resposta de propósito: ele pode ecoar o remetente configurado ou
     * outro detalhe de configuração, e o status já basta para orientar o diagnóstico.
     */
    public static final String RESEND_FALHA_HTTP_MESSAGE =
            "Falha ao enviar e-mail pelo Resend: destinatario={} finalidade={} status={}. "
                    + "Corpo da resposta omitido de propósito. Status 401 ou 403 costuma ser "
                    + "ABASTECEFACIL_EMAIL_API_KEY inválida; 422, remetente não verificado.";

    public static final String RESEND_FALHA_REDE_MESSAGE =
            "Falha de rede ao enviar e-mail pelo Resend: destinatario={} finalidade={}";

    // -------------------------------------------------------- mensagens de erro

    /**
     * Mensagem devolvida ao cliente, igual para toda falha de envio. O diagnóstico fica
     * no log: status HTTP e causa não viajam na resposta.
     */
    public static final String ENVIO_FALHOU_MESSAGE =
            "Não foi possível enviar o e-mail no momento. Tente novamente em alguns instantes.";

    public static final String PROVEDOR_DESCONHECIDO_MESSAGE =
            "abastecefacil.email.provedor inválido: '%s'. Valores aceitos: '%s' e '%s'. "
                    + "Ajuste ABASTECEFACIL_EMAIL_PROVEDOR.";

    /**
     * O valor recebido NUNCA entra nesta mensagem, pelo mesmo motivo de
     * {@link AdministradorInicialConstants#HASH_INVALIDO_MESSAGE}: se alguém colou a
     * chave errada, o texto dela não pode ir para o log.
     */
    public static final String CHAVE_AUSENTE_MESSAGE =
            "abastecefacil.email.provedor está em 'resend' mas a chave de API não foi "
                    + "configurada. Defina ABASTECEFACIL_EMAIL_API_KEY. O valor recebido não é "
                    + "registrado em lugar nenhum.";

    public static final String REMETENTE_AUSENTE_MESSAGE =
            "abastecefacil.email.provedor está em 'resend' mas o remetente não foi "
                    + "configurado. Defina ABASTECEFACIL_EMAIL_REMETENTE com um endereço "
                    + "verificado no Resend, no formato 'Nome <conta@dominio>'.";
}
