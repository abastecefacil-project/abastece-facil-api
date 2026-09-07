package com.github.api_abastecefacil.constants;

public class UserConstants {
    private UserConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final String USER_NOT_FOUND_MESSAGE = "Usuário não encontrado";
    public static final String USER_ALREADY_DELETED_MESSAGE = "Usuário deletado";
    public static final String EMAIL_ALREADY_EXISTS_MESSAGE = "Já existe um usuário cadastrado com esse e-mail";
    public static final String MATRICULA_INVALIDA_MESSAGE = "Matrícula deve conter de 4 a 12 dígitos";
    public static final String TELEFONE_INVALIDO_MESSAGE = "Telefone inválido. Use o formato (47) 99999-8888";

    // ------------------------------------------------------------------ S2a

    public static final String MATRICULA_OBRIGATORIA_MESSAGE =
            "Matrícula é obrigatória para os perfis COLABORADOR e GESTOR_FROTA";

    public static final String REGIONAL_OBRIGATORIA_MESSAGE =
            "Regional é obrigatória para os perfis COLABORADOR e GESTOR_FROTA";

    public static final String MATRICULA_ALREADY_EXISTS_MESSAGE =
            "Já existe um usuário cadastrado com essa matrícula";

    /**
     * A mensagem lista os domínios aceitos de propósito. Quem digitou o endereço foi o
     * gestor, no formulário, e o caso esmagadoramente provável é erro de digitação — dizer
     * "domínio não permitido" sem dizer quais são deixa a pessoa sem saída. Não há
     * vazamento: a lista é configuração operacional, não segredo.
     */
    public static final String DOMINIO_EMAIL_NAO_PERMITIDO_MESSAGE =
            "O e-mail deve pertencer a um domínio corporativo autorizado. Domínios aceitos: %s";

    public static final String PERFIL_NAO_PERMITIDO_MESSAGE =
            "Seu perfil não permite criar usuários com o perfil solicitado";

    public static final String REGIONAL_NAO_PERMITIDA_MESSAGE =
            "Gestor de frota só pode cadastrar usuários na própria regional";

    /**
     * Nome do índice parcial único de {@code users.matricula}, criado pela V4. É lido da
     * mensagem da {@code DataIntegrityViolationException} para distinguir colisão de
     * matrícula de colisão de e-mail no backstop de corrida do {@code UserService}.
     */
    public static final String INDICE_MATRICULA = "uk_users_matricula";

    // ----------------------------------------------------------------- S2b1

    /**
     * Rota do frontend para onde o link de ativação aponta.
     *
     * <p><b>Este é o contrato entre backend e frontend, definido aqui no S2b1 porque o
     * link precisa existir antes de a tela existir.</b> O S6 implementa esta rota; se ela
     * mudar de um lado, muda dos dois. Registrada também na §5 do CLAUDE.md.
     *
     * <p>O token vai em query string, e não em path, porque é um valor opaco de 43
     * caracteres em Base64 URL-safe — sem {@code +}, {@code /} ou {@code =}, então não
     * precisa de escape e não colide com a separação de segmentos da URL.
     */
    public static final String ROTA_DEFINIR_SENHA = "/definir-senha?token=";

    public static final String SENHA_JA_DEFINIDA_MESSAGE =
            "Este usuário já definiu a senha e não precisa de novo convite. "
                    + "Para trocar a senha, use a recuperação de senha.";

    public static final String CONVITE_ENVIADO_LOG =
            "Convite de ativação enviado: usuario={} perfil={}";

    /**
     * A URL NÃO entra nesta mensagem: ela contém o token em claro, e só o
     * {@code EnviadorEmailLog} pode registrá-lo. Ver §6 do CLAUDE.md.
     */
    public static final String CONVITE_FALHOU_LOG =
            "Falha ao enviar convite de ativação: usuario={} perfil={}. O usuário FOI criado "
                    + "e continua sem acesso até um reenvio bem-sucedido "
                    + "(POST /api/users/{}/reenviar-ativacao).";

    // ------------------------------------------------------------------ S3

    public static final int SENHA_TAMANHO_MINIMO = 10;

    /**
     * Comprimento mínimo de uma palavra do nome para entrar na checagem de "senha contém
     * dados pessoais".
     *
     * <p>O corte existe para não reprovar senha legítima por causa de nome curto: com
     * limiar 3, alguém chamado "Ana" não poderia usar {@code banana123456}. Em 4 ainda
     * sobra falso positivo — "Lima" reprova {@code climatempo1} —, e isso é aceito de
     * propósito: o custo é a pessoa ler a mensagem e escolher outra senha, contra o de
     * deixar passar {@code souza2024}.
     *
     * <p>Vale também para a <b>parte local do e-mail</b>, pelo mesmo motivo:
     * {@code ana@fiesc.org.br} reprovaria {@code banana123456}. O e-mail <b>inteiro</b> é
     * a única exceção — é longo o bastante para nunca casar por acidente, então entra sem
     * limiar.
     */
    public static final int SENHA_NOME_PALAVRA_MINIMA = 4;

    public static final String SENHA_FRACA_MESSAGE =
            "A senha deve ter no mínimo 10 caracteres e conter pelo menos uma letra e um número";

    /**
     * Diz o motivo sem revelar o critério: não lista a palavra detectada nem o limiar de
     * {@link #SENHA_NOME_PALAVRA_MINIMA} caracteres. Detalhar transformaria a mensagem
     * num manual de como contornar a regra.
     *
     * <p>A senha recebida nunca entra aqui, nem em log, nem em exceção — mesmo cuidado do
     * A3 com o hash e do M3 com a chave de API.
     */
    public static final String SENHA_COM_DADOS_PESSOAIS_MESSAGE =
            "A senha não pode conter o seu nome ou o seu e-mail";

    // ----------------------------------------------------------------- P0.4c

    /**
     * Mensagens de autorizacao de leitura, edicao e exclusao de usuario.
     *
     * <p>Sao distintas das do S2a de proposito. {@link #PERFIL_NAO_PERMITIDO_MESSAGE} e
     * {@link #REGIONAL_NAO_PERMITIDA_MESSAGE} falam em "criar" e "cadastrar", que e o que
     * o S2a autoriza; reusa-las em GET, PATCH e DELETE diria ao usuario que ele nao pode
     * criar alguem quando o que ele tentou foi ler. O campo "error" do ErrorResponse
     * continua o mesmo (PERFIL_NAO_PERMITIDO / REGIONAL_NAO_PERMITIDA) porque a acao
     * corretiva e a mesma: falar com quem tem o perfil, ou com a propria regional.
     */
    public static final String PERFIL_NAO_PERMITIDO_LEITURA_MESSAGE =
            "Seu perfil não permite consultar outros usuários";

    public static final String REGIONAL_NAO_PERMITIDA_LEITURA_MESSAGE =
            "Gestor de frota só pode consultar usuários da própria regional";

    public static final String PERFIL_NAO_PERMITIDO_EDICAO_MESSAGE =
            "Seu perfil não permite alterar este usuário";

    public static final String REGIONAL_NAO_PERMITIDA_EDICAO_MESSAGE =
            "Gestor de frota só pode alterar usuários da própria regional";

    public static final String PERFIL_NAO_PERMITIDO_EXCLUSAO_MESSAGE =
            "Somente um administrador pode excluir usuários";

    /**
     * Excluir a si mesmo e sempre recusado, inclusive para ADMINISTRADOR. A exclusao e
     * logica (is_active = false) e, desde o S3, um usuario inativo deixa de autenticar --
     * entao um administrador que se exclui perde o proprio acesso na hora, e como nao ha
     * endpoint que reative ninguem, so um UPDATE manual no banco o traria de volta. Se ele
     * for o unico administrador, o sistema fica sem caminho administrativo.
     */
    public static final String AUTO_EXCLUSAO_NAO_PERMITIDA_MESSAGE =
            "Você não pode excluir a própria conta";

    /**
     * O PATCH so aceita senha quando o alvo e o proprio autor.
     *
     * <p>Em nenhum outro ponto do sistema alguem escolhe a senha de terceiro: o
     * CreateUserRequest do S2a nao tem campo de senha, a ativacao do S3 e a propria pessoa
     * definindo, e a recuperacao (S4) tambem sera. O PATCH era a unica excecao, e era por
     * ela que qualquer autenticado trocava a senha de um administrador e assumia a conta.
     */
    public static final String SENHA_DE_TERCEIRO_MESSAGE =
            "A senha só pode ser alterada pelo próprio usuário. "
                    + "Para outra pessoa, use a recuperação de senha.";
}
