package com.github.api_abastecefacil.constants;

import java.util.regex.Pattern;

public final class AdministradorInicialConstants {

    private AdministradorInicialConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    /**
     * {@code users.name} é NOT NULL, então o nome precisa de default para o caso de só
     * e-mail e hash serem informados — que é a configuração mínima esperada.
     */
    public static final String NOME_PADRAO = "Administrador";

    /**
     * Formato de um hash BCrypt: prefixo, custo de dois dígitos e 53 caracteres de salt
     * mais digest, num total de 60.
     *
     * <p>Aceita os três prefixos em uso — {@code $2a$}, {@code $2b$} e {@code $2y$} — e
     * não apenas o {@code $2a$} que o {@code BCryptPasswordEncoder} do Spring gera por
     * padrão: um hash produzido por outra ferramenta é igualmente válido e o
     * {@code BCrypt} verifica os três. O comprimento fixo também barra hash truncado no
     * copiar e colar, que passaria pela checagem de prefixo e falharia só no login.
     */
    public static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    public static final String CONFIG_AUSENTE_MESSAGE =
            "Administrador inicial: criação pulada, ABASTECEFACIL_ADMIN_EMAIL e/ou "
                    + "ABASTECEFACIL_ADMIN_SENHA_HASH não definidas. Isso é esperado em "
                    + "desenvolvimento; em produção, ver README, seção 'Gerar o hash BCrypt'.";

    /**
     * O valor recebido NUNCA entra nesta mensagem. Se alguém colou a senha em texto plano
     * por engano, logá-la transformaria um erro de configuração em vazamento.
     */
    public static final String HASH_INVALIDO_MESSAGE =
            "Administrador inicial: ABASTECEFACIL_ADMIN_SENHA_HASH não tem formato de hash "
                    + "BCrypt e o usuário NÃO foi criado. Esperado algo como "
                    + "$2a$10$<53 caracteres>. Ver README, seção 'Gerar o hash BCrypt'.";

    public static final String RESUMO_FALHA_MESSAGE =
            "Administrador inicial NÃO criado — {}. Ver a linha de ERROR anterior.";

    public static final String RESUMO_HASH_INVALIDO = "hash BCrypt inválido";

    public static final String JA_EXISTE_MESSAGE =
            "Administrador inicial: {} já existe e já é ADMINISTRADOR, nada a fazer.";

    public static final String EMAIL_OCUPADO_MESSAGE =
            "Administrador inicial: {} já existe com o perfil {}, e NADA foi alterado. "
                    + "Nenhum administrador foi criado. Use outro e-mail em "
                    + "ABASTECEFACIL_ADMIN_EMAIL, ou promova este usuário manualmente.";

    public static final String CRIADO_MESSAGE =
            "Administrador inicial criado: {} (perfil ADMINISTRADOR).";

    public static final String CORRIDA_MESSAGE =
            "Administrador inicial: {} foi criado concorrentemente por outra instância; "
                    + "nada a fazer.";
}
