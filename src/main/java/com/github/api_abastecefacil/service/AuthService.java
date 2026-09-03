package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.AtivacaoRequest;
import com.github.api_abastecefacil.dto.auth.AtivacaoValidacaoResponse;
import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.exception.InvalidLoginException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PasswordNotSetException;
import com.github.api_abastecefacil.exception.TokenInvalidoException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
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

import java.util.Map;

import static com.github.api_abastecefacil.constants.AuthConstants.*;
import static com.github.api_abastecefacil.constants.TokenAcessoConstants.TOKEN_INVALIDO_MESSAGE;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenAcessoService tokenAcessoService;
    private final PasswordEncoder passwordEncoder;
    private final long jwtExpiration;
    private final long jwtExpirationColaborador;

    public AuthService(
            UserRepository userRepository,
            UserMapper userMapper,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            CustomUserDetailsService customUserDetailsService,
            TokenAcessoService tokenAcessoService,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.expiration:86400000}") long jwtExpiration,
            @Value("${jwt.expiration-colaborador:2592000000}") long jwtExpirationColaborador
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
        this.tokenAcessoService = tokenAcessoService;
        this.passwordEncoder = passwordEncoder;
        this.jwtExpiration = jwtExpiration;
        this.jwtExpirationColaborador = jwtExpirationColaborador;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateEmailDoesNotExist(request.email());

        User user = createAndSaveUser(request);
        String token = generateTokenFor(user);

        return createAuthResponse(token, REGISTER_SUCCESS_MESSAGE, user);
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
    public AtivacaoValidacaoResponse validarTokenAtivacao(String token) {
        return tokenAcessoService.emailDeTokenValido(token, FinalidadeToken.ATIVACAO)
                .flatMap(userRepository::findByEmail)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .map(user -> new AtivacaoValidacaoResponse(true, user.getName()))
                .orElseGet(AtivacaoValidacaoResponse::invalido);
    }

    /**
     * Define a senha do convidado, consome o token e já devolve a pessoa autenticada —
     * o mesmo {@link AuthResponse} do login, para ela não precisar digitar a senha que
     * acabou de escolher.
     *
     * <p><b>A ordem das etapas é a parte que importa.</b> O caminho ingênuo — consumir o
     * token e depois validar a senha — queima o link quando a senha é fraca: a pessoa
     * erra a política, o token vira usado, e ela precisa pedir um reenvio para tentar de
     * novo. Por isso a senha é validada contra uma leitura <b>não destrutiva</b> e o
     * consumo vem depois.
     *
     * <p>O {@code validarEConsumir} continua sendo a autoridade sobre a validade: é ele
     * que fecha a janela de corrida do M2. A leitura otimista do início é conveniência,
     * não decisão — se algo mudar entre as duas, o {@code UPDATE} condicional rejeita e o
     * resultado é a mesma {@code TokenInvalidoException}.
     */
    @Transactional
    public AuthResponse ativarConta(AtivacaoRequest request) {
        String email = tokenAcessoService.emailDeTokenValido(request.token(), FinalidadeToken.ATIVACAO)
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

        tokenAcessoService.validarEConsumir(request.token(), FinalidadeToken.ATIVACAO);

        user.setPassword(passwordEncoder.encode(request.senha()));
        // Mantem o invariante password != null <=> senhaDefinida, como
        // UserService.updatePasswordIfProvided.
        user.setSenhaDefinida(true);
        userRepository.save(user);

        return createAuthResponse(generateTokenFor(user), ATIVACAO_SUCCESS_MESSAGE, user);
    }

    private void validateEmailDoesNotExist(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(USER_ALREADY_EXISTS_MESSAGE);
        }
    }

    private User createAndSaveUser(RegisterRequest request) {
        User user = userMapper.toEntity(request);
        return userRepository.save(user);
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
