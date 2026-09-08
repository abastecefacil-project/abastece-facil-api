package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.DefinicaoSenhaRequest;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RecuperacaoRequest;
import com.github.api_abastecefacil.dto.auth.RecuperacaoResponse;
import com.github.api_abastecefacil.dto.auth.TokenValidacaoResponse;
import com.github.api_abastecefacil.exception.InvalidLoginException;
import com.github.api_abastecefacil.exception.LimiteSolicitacoesExcedidoException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PasswordNotSetException;
import com.github.api_abastecefacil.exception.TokenInvalidoException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import com.github.api_abastecefacil.validation.UserValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

import static com.github.api_abastecefacil.constants.AuthConstants.*;
import static com.github.api_abastecefacil.constants.RateLimitConstants.*;
import static com.github.api_abastecefacil.constants.TokenAcessoConstants.TOKEN_INVALIDO_MESSAGE;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenAcessoService tokenAcessoService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final long jwtExpiration;
    private final long jwtExpirationColaborador;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            CustomUserDetailsService customUserDetailsService,
            TokenAcessoService tokenAcessoService,
            PasswordEncoder passwordEncoder,
            RateLimitService rateLimitService,
            RecuperacaoSenhaService recuperacaoSenhaService,
            @Value("${jwt.expiration:86400000}") long jwtExpiration,
            @Value("${jwt.expiration-colaborador:2592000000}") long jwtExpirationColaborador
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
        this.tokenAcessoService = tokenAcessoService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.jwtExpiration = jwtExpiration;
        this.jwtExpirationColaborador = jwtExpirationColaborador;
    }

    public AuthResponse login(LoginRequest request) {
        User user = findUserByEmailOrThrow(request.email());
        validateUserIsActive(user);
        validateSenhaDefinida(user);
        authenticateUser(request.email(), request.password());
        String token = generateTokenFor(user);
        return createAuthResponse(token, LOGIN_SUCCESS_MESSAGE, user);
    }

    /**
     * Sonda do link de ativação. <b>Não consome o token</b> e não lança: devolve
     * {@code valido = false} para as quatro rejeições, sem distinguir qual ocorreu.
     *
     * <p>Chamar N vezes deixa o token exatamente como estava — é o que permite ao
     * frontend abrir a tela, o usuário recarregar a página, e o link continuar servindo.
     */
    public TokenValidacaoResponse validarTokenAtivacao(String token) {
        return validarToken(token, FinalidadeToken.ATIVACAO);
    }

    /**
     * Sonda do link de recuperação — a gêmea de {@link #validarTokenAtivacao}, com a
     * outra finalidade. Também não consome, também responde 200 sempre.
     *
     * <p>Um token de ativação apresentado aqui é rejeitado como qualquer outro inválido,
     * e o contrário também: a finalidade faz parte do predicado de validade.
     */
    public TokenValidacaoResponse validarTokenRecuperacao(String token) {
        return validarToken(token, FinalidadeToken.RECUPERACAO);
    }

    /**
     * Define a senha do convidado, consome o token e já devolve a pessoa autenticada —
     * o mesmo {@link AuthResponse} do login, para ela não precisar digitar a senha que
     * acabou de escolher.
     *
     * <p>O corpo é compartilhado com a redefinição do S4; a ordem entre validar a senha e
     * consumir o token, que é a parte delicada, está explicada em
     * {@link #definirSenhaComToken}.
     */
    @Transactional
    public AuthResponse ativarConta(DefinicaoSenhaRequest request) {
        return definirSenhaComToken(request, FinalidadeToken.ATIVACAO, ATIVACAO_SUCCESS_MESSAGE);
    }

    /**
     * Solicita a recuperação de senha. <b>Responde sempre igual</b>, e é o método inteiro.
     *
     * <p>Repare no que <b>não</b> está aqui: nenhuma consulta ao usuário, nenhum
     * {@code if} sobre existência, atividade ou senha definida, nenhuma chamada ao
     * provedor de e-mail. Toda decisão acontece depois, em outra thread
     * ({@code RecuperacaoSenhaService}), e é isso que faz "e-mail cadastrado" e "e-mail
     * inexistente" serem indistinguíveis <b>por construção</b> — não por cuidado de quem
     * escreveu, e não por atraso artificial calibrado.
     *
     * <p>O corpo e o status já seriam idênticos com o envio síncrono; o <b>tempo</b> não
     * seria. Emitir token e chamar o Resend custa centenas de milissegundos que só um
     * e-mail existente pagaria, e cronometrar as respostas revelaria quem tem conta. Como
     * a resposta sai antes de qualquer I/O começar, não há o que vazar.
     *
     * <p>O limite de requisições é a única coisa que roda antes, e roda <b>para todo
     * mundo</b>: aplicá-lo apenas a e-mails existentes faria do próprio 429 o oráculo que
     * o resto do desenho evita.
     */
    public RecuperacaoResponse solicitarRecuperacao(RecuperacaoRequest request, String ipSolicitante) {
        aplicarLimiteDeSolicitacoes(request.email(), ipSolicitante);

        recuperacaoSenhaService.enviarLink(request.email(), ipSolicitante);

        return RecuperacaoResponse.padrao();
    }

    /**
     * Define a senha nova a partir do link de recuperação e devolve a pessoa autenticada,
     * como a ativação faz.
     *
     * <p>Compartilha corpo inteiro com {@link #ativarConta} — inclusive a ordem entre
     * validar e consumir, que é a parte delicada. Ver {@link #definirSenhaComToken}.
     */
    @Transactional
    public AuthResponse redefinirSenha(DefinicaoSenhaRequest request) {
        return definirSenhaComToken(request, FinalidadeToken.RECUPERACAO, SENHA_REDEFINIDA_SUCCESS_MESSAGE);
    }

    /**
     * Corpo comum da ativação (S3) e da redefinição (S4).
     *
     * <p><b>A ordem das etapas é a parte que importa, e vale para as duas.</b> O caminho
     * ingênuo — consumir o token e depois validar a senha — queima o link quando a senha é
     * fraca: a pessoa erra a política, o token vira usado, e ela precisa pedir outro link
     * só para tentar de novo. Por isso a senha é validada contra uma leitura <b>não
     * destrutiva</b> e o consumo vem depois.
     *
     * <p>O {@code validarEConsumir} continua sendo a autoridade sobre a validade: é ele
     * que fecha a janela de corrida do M2. A leitura otimista do início é conveniência,
     * não decisão — se algo mudar entre as duas, o {@code UPDATE} condicional rejeita e o
     * resultado é a mesma {@code TokenInvalidoException}.
     *
     * <p>A finalidade é parâmetro, e é o que impede um token de ativação de servir na
     * recuperação e vice-versa: ela entra no predicado de validade das duas consultas.
     */
    private AuthResponse definirSenhaComToken(DefinicaoSenhaRequest request,
                                              FinalidadeToken finalidade,
                                              String mensagemSucesso) {
        String email = tokenAcessoService.emailDeTokenValido(request.token(), finalidade)
                .orElseThrow(() -> new TokenInvalidoException(TOKEN_INVALIDO_MESSAGE));

        // Mesma excecao generica do token: um usuario removido depois da emissao nao deve
        // produzir um erro diferente, que revelaria que o token em si era bom.
        // Inativo cai na MESMA excecao generica, e nao no 401 de usuario inativo do login:
        // aqui o interlocutor e alguem segurando um link, e responder algo diferente
        // confirmaria que aquele token era bom e que a conta existe.
        User user = userRepository.findByEmail(email)
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .orElseThrow(() -> new TokenInvalidoException(TOKEN_INVALIDO_MESSAGE));

        // Antes de consumir: senha fraca nao pode custar o link.
        UserValidator.validarSenha(request.senha(), user.getEmail(), user.getName());

        tokenAcessoService.validarEConsumir(request.token(), finalidade);

        user.setPassword(passwordEncoder.encode(request.senha()));
        // Mantem o invariante password != null <=> senhaDefinida, como
        // UserService.updatePasswordIfProvided.
        user.setSenhaDefinida(true);
        userRepository.save(user);

        // Qualquer outro link pendente daquele e-mail e a capacidade de definir a senha de
        // novo, seja qual for a finalidade com que foi emitido -- um convite esquecido na
        // caixa de entrada serve para isso tao bem quanto um link de recuperacao. Vale
        // para os dois fluxos: quem acabou de ativar a conta tambem nao deve deixar um
        // pedido de recuperacao antigo valendo.
        tokenAcessoService.invalidarTodosPendentes(email);

        return createAuthResponse(generateTokenFor(user), mensagemSucesso, user);
    }

    /**
     * Corpo comum das duas sondas. Não consome, não lança, e devolve o mesmo
     * {@code valido = false} para as quatro rejeições.
     */
    private TokenValidacaoResponse validarToken(String token, FinalidadeToken finalidade) {
        return tokenAcessoService.emailDeTokenValido(token, finalidade)
                .flatMap(userRepository::findByEmail)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .map(user -> new TokenValidacaoResponse(true, user.getName()))
                .orElseGet(TokenValidacaoResponse::invalido);
    }

    /**
     * Aplica os dois limites da recuperação: por e-mail e por IP.
     *
     * <p><b>Confere os dois antes de registrar qualquer um.</b> Se a consulta já
     * contabilizasse, uma solicitação recusada pelo limite de IP teria consumido cota do
     * e-mail no caminho — e um usuário legítimo pagaria pelo excesso de um vizinho de rede
     * com o mesmo endereço público.
     *
     * <p>A chave do e-mail vai em caixa baixa: sem isso, alternar a capitalização daria
     * uma cota nova a cada variação, e o limite seria contornável digitando
     * {@code Fulano@} em vez de {@code fulano@}. O e-mail usado na busca do usuário não é
     * alterado — só a chave do contador.
     */
    private void aplicarLimiteDeSolicitacoes(String email, String ipSolicitante) {
        String chaveEmail = CHAVE_RECUPERACAO_EMAIL + email.toLowerCase(Locale.ROOT);
        String chaveIp = CHAVE_RECUPERACAO_IP + ipSolicitante;

        boolean excedeu =
                rateLimitService.excedeu(chaveEmail, LIMITE_RECUPERACAO_EMAIL, JANELA_RECUPERACAO_EMAIL)
                        || rateLimitService.excedeu(chaveIp, LIMITE_RECUPERACAO_IP, JANELA_RECUPERACAO_IP);

        if (excedeu) {
            throw new LimiteSolicitacoesExcedidoException(LIMITE_SOLICITACOES_EXCEDIDO_MESSAGE);
        }

        rateLimitService.registrar(chaveEmail);
        rateLimitService.registrar(chaveIp);
    }

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
    }

    private void validateUserIsActive(User user) {
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new InvalidLoginException(USER_INACTIVE_MESSAGE);
        }
    }

    /**
     * Roda ANTES de authenticateUser, ou seja, antes de o AuthenticationManager e
     * portanto o PasswordEncoder serem tocados. As duas condicoes sao propositais: uma
     * linha inconsistente (senha_definida = true com password nulo, possivel por UPDATE
     * manual) tambem e barrada aqui, em vez de virar erro mais adiante.
     */
    private void validateSenhaDefinida(User user) {
        if (Boolean.FALSE.equals(user.getSenhaDefinida()) || user.getPassword() == null) {
            throw new PasswordNotSetException(PASSWORD_NOT_SET_MESSAGE);
        }
    }

    private void authenticateUser(String email, String password) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, password);
            authenticationManager.authenticate(authToken);
        } catch (BadCredentialsException e) {
            throw new InvalidLoginException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    /**
     * O perfil viaja no token como claim para o frontend poder rotear sem uma
     * chamada extra. A expiração varia por perfil desde o S3 — ver
     * {@link #resolverExpiracao}.
     */
    private String generateTokenFor(User user) {
        UserDetails userDetails = customUserDetailsService.toUserDetails(user);
        Map<String, Object> extraClaims = Map.of(PERFIL_CLAIM, user.getPerfil().name());
        return jwtService.generateToken(extraClaims, userDetails, resolverExpiracao(user.getPerfil()));
    }

    /**
     * Sessão longa para COLABORADOR, curta para quem administra.
     *
     * <p>O colaborador usa o sistema poucas vezes por ano: com sessão de 24 horas ele
     * encontrava a sessão vencida em toda visita e era obrigado a recuperar a senha
     * sempre — a dor que o cliente descreveu na reunião. Gestor e administrador usam o
     * sistema com frequência e têm poderes destrutivos, então mantêm o prazo curto.
     *
     * <p>Única ramificação por perfil do serviço, em switch expression sem
     * {@code default}, como {@code TokenAcessoService.resolverHoras}: perfil novo passa a
     * quebrar a compilação aqui em vez de herdar em silêncio o prazo mais permissivo.
     */
    private long resolverExpiracao(Perfil perfil) {
        return switch (perfil) {
            case COLABORADOR -> jwtExpirationColaborador;
            case GESTOR_FROTA, ADMINISTRADOR -> jwtExpiration;
        };
    }

    private AuthResponse createAuthResponse(String token, String message, User user) {
        return new AuthResponse(token, TOKEN_TYPE, message, user.getPerfil());
    }
}
