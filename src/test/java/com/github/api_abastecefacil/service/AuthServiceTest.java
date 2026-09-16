package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.DefinicaoSenhaRequest;
import com.github.api_abastecefacil.dto.auth.TokenValidacaoResponse;
import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RecuperacaoRequest;
import com.github.api_abastecefacil.dto.auth.RecuperacaoResponse;
import com.github.api_abastecefacil.exception.InvalidLoginException;
import com.github.api_abastecefacil.exception.LimiteSolicitacoesExcedidoException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PasswordNotSetException;
import com.github.api_abastecefacil.exception.SenhaFracaException;
import com.github.api_abastecefacil.exception.TokenInvalidoException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.AuthConstants.RECUPERACAO_SOLICITADA_MESSAGE;
import static com.github.api_abastecefacil.constants.AuthConstants.SENHA_REDEFINIDA_SUCCESS_MESSAGE;
import static com.github.api_abastecefacil.constants.RateLimitConstants.*;
import static com.github.api_abastecefacil.constants.TokenAcessoConstants.TOKEN_INVALIDO_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long JWT_EXPIRACAO = 86_400_000L;            // 24h
    private static final long JWT_EXPIRACAO_COLABORADOR = 2_592_000_000L; // 30 dias

    private static final String TOKEN_ATIVACAO = "token-de-ativacao-em-claro";
    private static final String TOKEN_RECUPERACAO = "token-de-recuperacao-em-claro";
    private static final String IP = "10.0.0.7";
    private static final String SENHA_BOA = "Chuva8Verde";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RecuperacaoSenhaService recuperacaoSenhaService;

    private AuthService authService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    private User user;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        // @InjectMocks nao serve: o construtor recebe dois long de configuracao, e a
        // expiracao por perfil e justamente o que varios testes exercitam.
        authService = new AuthService(
                userRepository, jwtService, authenticationManager,
                customUserDetailsService, tokenAcessoService, passwordEncoder,
                rateLimitService, recuperacaoSenhaService,
                JWT_EXPIRACAO, JWT_EXPIRACAO_COLABORADOR);

        user = new User()
                .setId(1L)
                .setName("Test User")
                .setEmail("user@test.com")
                .setPassword("encodedPassword")
                .setActive(true)
                .setPerfil(Perfil.COLABORADOR)
                .setSenhaDefinida(true);

        userDetails = new org.springframework.security.core.userdetails.User(
                "user@test.com",
                "encodedPassword",
                List.of(new SimpleGrantedAuthority("ROLE_COLABORADOR"))
        );
    }

    // --------------------------------------------------------------------- login

    @Test
    void login_ShouldAuthenticateAndReturnToken() {
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.perfil()).isEqualTo(Perfil.COLABORADOR);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_ShouldIncludePerfilClaimInToken() {
        user.setPerfil(Perfil.ADMINISTRADOR);
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.login(request);

        verify(jwtService).generateToken(claimsCaptor.capture(), any(UserDetails.class), anyLong());
        assertThat(claimsCaptor.getValue()).containsEntry("perfil", "ADMINISTRADOR");
    }

    @Test
    void login_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest("unknown@test.com", "password123");
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.login(request));
    }

    @Test
    void login_ShouldThrowInvalidLoginException_WhenUserIsInactive() {
        user.setActive(false);
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(InvalidLoginException.class, () -> authService.login(request));
    }

    @Test
    void login_ShouldThrowPasswordNotSetException_WhenSenhaNaoDefinida() {
        user.setSenhaDefinida(false);
        LoginRequest request = new LoginRequest("user@test.com", "qualquerSenha123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(PasswordNotSetException.class, () -> authService.login(request));

        // A restricao central do S1: rejeitar antes de qualquer chamada ao PasswordEncoder.
        verify(authenticationManager, never()).authenticate(any());
        verify(jwtService, never()).generateToken(anyMap(), any(UserDetails.class), anyLong());
    }

    @Test
    void login_ShouldThrowPasswordNotSetException_WhenPasswordIsNull() {
        user.setPassword(null).setSenhaDefinida(true);
        LoginRequest request = new LoginRequest("user@test.com", "qualquerSenha123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(PasswordNotSetException.class, () -> authService.login(request));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_ShouldThrowInvalidLoginException_WhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest("user@test.com", "wrongpassword");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidLoginException.class, () -> authService.login(request));
    }

    // ------------------------------------------------ expiracao do JWT por perfil

    private long expiracaoEmitidaPara(Perfil perfil) {
        user.setPerfil(perfil);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.login(new LoginRequest("user@test.com", "senha"));

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(jwtService).generateToken(anyMap(), any(UserDetails.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void login_ShouldIssueLongSession_ForColaborador() {
        // 30 dias: o colaborador usa o sistema poucas vezes por ano e, com 24h, encontrava
        // a sessao sempre vencida -- a dor relatada pelo cliente.
        assertThat(expiracaoEmitidaPara(Perfil.COLABORADOR)).isEqualTo(JWT_EXPIRACAO_COLABORADOR);
    }

    @Test
    void login_ShouldIssueShortSession_ForGestorFrota() {
        assertThat(expiracaoEmitidaPara(Perfil.GESTOR_FROTA)).isEqualTo(JWT_EXPIRACAO);
    }

    @Test
    void login_ShouldIssueShortSession_ForAdministrador() {
        assertThat(expiracaoEmitidaPara(Perfil.ADMINISTRADOR)).isEqualTo(JWT_EXPIRACAO);
    }

    // ---------------------------------------------------------- sonda de ativacao

    @Test
    void validarTokenAtivacao_ShouldReturnValidWithName_WhenTokenIsGood() {
        user.setSenhaDefinida(false).setPassword(null);
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        TokenValidacaoResponse response = authService.validarTokenAtivacao(TOKEN_ATIVACAO);

        assertThat(response.valido()).isTrue();
        assertThat(response.nome()).isEqualTo("Test User");
    }

    @Test
    void validarTokenAtivacao_ShouldNeverConsumeTheToken() {
        // O ponto do endpoint: abrir a tela nao pode queimar o link. Chamar duas vezes
        // seguidas continua respondendo valido.
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThat(authService.validarTokenAtivacao(TOKEN_ATIVACAO).valido()).isTrue();
        assertThat(authService.validarTokenAtivacao(TOKEN_ATIVACAO).valido()).isTrue();

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
    }

    @Test
    void validarTokenAtivacao_ShouldReturnTheSameInvalidResponse_ForEveryRejection() {
        // Inexistente, expirado, ja usado e finalidade divergente caem todos no mesmo
        // Optional.empty() do service: a resposta nao pode diferenciar qual ocorreu.
        when(tokenAcessoService.emailDeTokenValido(anyString(), any())).thenReturn(Optional.empty());

        TokenValidacaoResponse response = authService.validarTokenAtivacao("qualquer");

        assertThat(response.valido()).isFalse();
        assertThat(response.nome()).isNull();
    }

    @Test
    void validarTokenAtivacao_ShouldReturnInvalid_WhenUserIsInactive() {
        user.setActive(false);
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThat(authService.validarTokenAtivacao(TOKEN_ATIVACAO).valido()).isFalse();
    }

    // ---------------------------------------------------------------- ativarConta

    private void stubTokenValido() {
        user.setSenhaDefinida(false).setPassword(null);
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
    }

    @Test
    void ativarConta_ShouldSetPasswordAndReturnAuthenticatedResponse() {
        stubTokenValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-bcrypt");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        AuthResponse response = authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.perfil()).isEqualTo(Perfil.COLABORADOR);
        assertThat(user.getPassword()).isEqualTo("hash-bcrypt");
        // Mantem o invariante password != null <=> senhaDefinida.
        assertThat(user.getSenhaDefinida()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void ativarConta_ShouldConsumeTheToken_WithAtivacaoFinalidade() {
        stubTokenValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-bcrypt");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA));

        verify(tokenAcessoService).validarEConsumir(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO);
    }

    @Test
    void ativarConta_ShouldNotConsumeTheToken_WhenPasswordIsWeak() {
        // A razao da ordem escolhida: consumir antes de validar queimaria o link a cada
        // senha fraca, e a pessoa teria de pedir reenvio para tentar de novo.
        stubTokenValido();

        assertThrows(SenhaFracaException.class,
                () -> authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, "curta1")));

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void ativarConta_ShouldRejectPasswordContainingTheUserName() {
        stubTokenValido();

        assertThrows(SenhaFracaException.class,
                () -> authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, "Test2026Segura")));

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
    }

    @Test
    void ativarConta_ShouldThrowTokenInvalido_WhenTokenIsNotValid() {
        when(tokenAcessoService.emailDeTokenValido(anyString(), any())).thenReturn(Optional.empty());

        TokenInvalidoException ex = assertThrows(TokenInvalidoException.class,
                () -> authService.ativarConta(new DefinicaoSenhaRequest("qualquer", SENHA_BOA)));

        assertThat(ex.getMessage()).isEqualTo(TOKEN_INVALIDO_MESSAGE);
        verify(userRepository, never()).save(any());
    }

    @Test
    void ativarConta_ShouldThrowTokenInvalido_WhenUserNoLongerExists() {
        // Mesma excecao generica: responder algo diferente confirmaria que o token era bom.
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("sumiu@test.com"));
        when(userRepository.findByEmail("sumiu@test.com")).thenReturn(Optional.empty());

        assertThrows(TokenInvalidoException.class,
                () -> authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA)));
    }

    @Test
    void ativarConta_ShouldThrowTokenInvalido_WhenUserIsInactive() {
        user.setActive(false);
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        // E nao o 401 de usuario inativo do login: aqui o interlocutor segura um link.
        assertThrows(TokenInvalidoException.class,
                () -> authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA)));
    }

    @Test
    void ativarConta_ShouldIssueLongSession_ForColaborador() {
        stubTokenValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-bcrypt");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA));

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(jwtService).generateToken(anyMap(), any(UserDetails.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(JWT_EXPIRACAO_COLABORADOR);
    }

    @Test
    void ativarConta_ShouldInvalidateEveryOtherPendingToken() {
        // Vale para os dois fluxos: quem acabou de ativar a conta tambem nao deve deixar
        // um pedido de recuperacao antigo valendo -- ele definiria a senha outra vez.
        stubTokenValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-bcrypt");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA));

        verify(tokenAcessoService).invalidarTodosPendentes("user@test.com");
    }

    // ------------------------------------------------------ solicitarRecuperacao

    /**
     * O criterio de aceite obrigatorio do S4: e-mail cadastrado e e-mail inexistente
     * produzem respostas identicas.
     *
     * <p>O teste vai alem de comparar os dois retornos. Ele prova que o AuthService NAO
     * CONSULTA o usuario -- verifyNoInteractions(userRepository) -- e e essa a garantia
     * de verdade: sem consulta nao ha ramo, sem ramo nao ha como divergir nem no corpo,
     * nem no status, nem no tempo. Comparar so as respostas passaria mesmo numa
     * implementacao que consultasse o banco e demorasse o dobro para quem existe.
     */
    @Test
    void solicitarRecuperacao_ShouldAnswerIdentically_ForKnownAndUnknownEmail() {
        RecuperacaoResponse paraCadastrado =
                authService.solicitarRecuperacao(new RecuperacaoRequest("user@test.com"), IP);
        RecuperacaoResponse paraInexistente =
                authService.solicitarRecuperacao(new RecuperacaoRequest("ninguem@test.com"), IP);

        assertThat(paraCadastrado).isEqualTo(paraInexistente);
        assertThat(paraCadastrado.message()).isEqualTo(RECUPERACAO_SOLICITADA_MESSAGE);
        verifyNoInteractions(userRepository);
    }

    @Test
    void solicitarRecuperacao_ShouldDelegateTheSendingWithoutInspectingTheUser() {
        authService.solicitarRecuperacao(new RecuperacaoRequest("user@test.com"), IP);

        // Quem resolve usuario, atividade e finalidade e o servico assincrono, depois de
        // a resposta ja ter saido.
        verify(recuperacaoSenhaService).enviarLink("user@test.com", IP);
        verifyNoInteractions(userRepository, tokenAcessoService, passwordEncoder);
    }

    @Test
    void solicitarRecuperacao_ShouldThrow429_WhenTheEmailLimitIsExceeded() {
        when(rateLimitService.excedeu(startsWith(CHAVE_RECUPERACAO_EMAIL),
                eq(LIMITE_RECUPERACAO_EMAIL), eq(JANELA_RECUPERACAO_EMAIL))).thenReturn(true);

        assertThrows(LimiteSolicitacoesExcedidoException.class,
                () -> authService.solicitarRecuperacao(new RecuperacaoRequest("user@test.com"), IP));

        verifyNoInteractions(recuperacaoSenhaService);
    }

    @Test
    void solicitarRecuperacao_ShouldThrow429_WhenTheIpLimitIsExceeded() {
        // O e-mail esta dentro da cota; quem estourou foi o IP -- e o pedido cai do mesmo
        // jeito, porque basta um dos dois limites.
        when(rateLimitService.excedeu(startsWith(CHAVE_RECUPERACAO_EMAIL),
                eq(LIMITE_RECUPERACAO_EMAIL), eq(JANELA_RECUPERACAO_EMAIL))).thenReturn(false);
        when(rateLimitService.excedeu(startsWith(CHAVE_RECUPERACAO_IP),
                eq(LIMITE_RECUPERACAO_IP), eq(JANELA_RECUPERACAO_IP))).thenReturn(true);

        assertThrows(LimiteSolicitacoesExcedidoException.class,
                () -> authService.solicitarRecuperacao(new RecuperacaoRequest("user@test.com"), IP));

        verifyNoInteractions(recuperacaoSenhaService);
    }

    @Test
    void solicitarRecuperacao_ShouldApplyTheLimit_EvenForAnUnknownEmail() {
        // Aplicar o limite so a e-mail existente faria do proprio 429 o oraculo de
        // existencia que o resto do desenho evita.
        authService.solicitarRecuperacao(new RecuperacaoRequest("ninguem@test.com"), IP);

        verify(rateLimitService).registrar(CHAVE_RECUPERACAO_EMAIL + "ninguem@test.com");
        verify(rateLimitService).registrar(CHAVE_RECUPERACAO_IP + IP);
    }

    @Test
    void solicitarRecuperacao_ShouldNotRegisterAnything_WhenTheLimitIsExceeded() {
        // Confere os dois limites antes de registrar qualquer um: uma solicitacao recusada
        // pelo IP nao pode consumir cota do e-mail no caminho.
        // O e-mail esta dentro da cota; quem estourou foi o IP -- e o pedido cai do mesmo
        // jeito, porque basta um dos dois limites.
        when(rateLimitService.excedeu(startsWith(CHAVE_RECUPERACAO_EMAIL),
                eq(LIMITE_RECUPERACAO_EMAIL), eq(JANELA_RECUPERACAO_EMAIL))).thenReturn(false);
        when(rateLimitService.excedeu(startsWith(CHAVE_RECUPERACAO_IP),
                eq(LIMITE_RECUPERACAO_IP), eq(JANELA_RECUPERACAO_IP))).thenReturn(true);

        assertThrows(LimiteSolicitacoesExcedidoException.class,
                () -> authService.solicitarRecuperacao(new RecuperacaoRequest("user@test.com"), IP));

        verify(rateLimitService, never()).registrar(anyString());
    }

    @Test
    void solicitarRecuperacao_ShouldLowercaseTheEmailKey() {
        // Sem isso, alternar a capitalizacao daria uma cota nova a cada variacao.
        authService.solicitarRecuperacao(new RecuperacaoRequest("USER@TEST.COM"), IP);

        verify(rateLimitService).registrar(CHAVE_RECUPERACAO_EMAIL + "user@test.com");
        // O e-mail usado no envio nao e alterado: so a chave do contador.
        verify(recuperacaoSenhaService).enviarLink("USER@TEST.COM", IP);
    }

    // ---------------------------------------------------------- redefinirSenha

    private void stubTokenRecuperacaoValido() {
        when(tokenAcessoService.emailDeTokenValido(TOKEN_RECUPERACAO, FinalidadeToken.RECUPERACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
    }

    @Test
    void redefinirSenha_ShouldSetPasswordAndReturnAuthenticatedResponse() {
        stubTokenRecuperacaoValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-novo");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        AuthResponse response = authService.redefinirSenha(new DefinicaoSenhaRequest(TOKEN_RECUPERACAO, SENHA_BOA));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.message()).isEqualTo(SENHA_REDEFINIDA_SUCCESS_MESSAGE);
        assertThat(user.getPassword()).isEqualTo("hash-novo");
        assertThat(user.getSenhaDefinida()).isTrue();
        verify(userRepository).save(user);
        verify(tokenAcessoService).validarEConsumir(TOKEN_RECUPERACAO, FinalidadeToken.RECUPERACAO);
    }

    @Test
    void redefinirSenha_ShouldInvalidateEveryOtherPendingToken() {
        // Depois da senha nova, qualquer link pendente daquele e-mail -- inclusive um
        // convite de ativacao esquecido na caixa de entrada -- e a capacidade de
        // defini-la outra vez.
        stubTokenRecuperacaoValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-novo");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.redefinirSenha(new DefinicaoSenhaRequest(TOKEN_RECUPERACAO, SENHA_BOA));

        verify(tokenAcessoService).invalidarTodosPendentes("user@test.com");
    }

    /**
     * Criterio de aceite obrigatorio, primeira metade: um token de ATIVACAO nao serve na
     * confirmacao da recuperacao. A finalidade entra no predicado de validade, entao a
     * consulta por RECUPERACAO nao o encontra.
     */
    @Test
    void redefinirSenha_ShouldRejectAnAtivacaoToken() {
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.RECUPERACAO))
                .thenReturn(Optional.empty());

        TokenInvalidoException ex = assertThrows(TokenInvalidoException.class,
                () -> authService.redefinirSenha(new DefinicaoSenhaRequest(TOKEN_ATIVACAO, SENHA_BOA)));

        assertThat(ex.getMessage()).isEqualTo(TOKEN_INVALIDO_MESSAGE);
        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
        verify(userRepository, never()).save(any());
    }

    /**
     * Criterio de aceite obrigatorio, segunda metade: o inverso tambem. Um token de
     * RECUPERACAO nao ativa conta nenhuma.
     */
    @Test
    void ativarConta_ShouldRejectARecuperacaoToken() {
        when(tokenAcessoService.emailDeTokenValido(TOKEN_RECUPERACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.empty());

        TokenInvalidoException ex = assertThrows(TokenInvalidoException.class,
                () -> authService.ativarConta(new DefinicaoSenhaRequest(TOKEN_RECUPERACAO, SENHA_BOA)));

        assertThat(ex.getMessage()).isEqualTo(TOKEN_INVALIDO_MESSAGE);
        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
    }

    @Test
    void redefinirSenha_ShouldNotConsumeTheToken_WhenPasswordIsWeak() {
        // Mesma razao da ativacao: consumir antes de validar queimaria o link a cada
        // senha fraca, e o link da recuperacao vale apenas uma hora.
        stubTokenRecuperacaoValido();

        assertThrows(SenhaFracaException.class,
                () -> authService.redefinirSenha(new DefinicaoSenhaRequest(TOKEN_RECUPERACAO, "curta1")));

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
        verify(tokenAcessoService, never()).invalidarTodosPendentes(anyString());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void redefinirSenha_ShouldThrowTokenInvalido_WhenUserIsInactive() {
        user.setActive(false);
        stubTokenRecuperacaoValido();

        assertThrows(TokenInvalidoException.class,
                () -> authService.redefinirSenha(new DefinicaoSenhaRequest(TOKEN_RECUPERACAO, SENHA_BOA)));
    }

    @Test
    void redefinirSenha_ShouldThrowTokenInvalido_WhenUserNoLongerExists() {
        when(tokenAcessoService.emailDeTokenValido(TOKEN_RECUPERACAO, FinalidadeToken.RECUPERACAO))
                .thenReturn(Optional.of("sumiu@test.com"));
        when(userRepository.findByEmail("sumiu@test.com")).thenReturn(Optional.empty());

        assertThrows(TokenInvalidoException.class,
                () -> authService.redefinirSenha(new DefinicaoSenhaRequest(TOKEN_RECUPERACAO, SENHA_BOA)));
    }

    // -------------------------------------------------- validarTokenRecuperacao

    @Test
    void validarTokenRecuperacao_ShouldReturnValidWithTheUserName() {
        stubTokenRecuperacaoValido();

        TokenValidacaoResponse response = authService.validarTokenRecuperacao(TOKEN_RECUPERACAO);

        assertThat(response.valido()).isTrue();
        assertThat(response.nome()).isEqualTo("Test User");
    }

    @Test
    void validarTokenRecuperacao_ShouldReturnInvalid_ForAnAtivacaoToken() {
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.RECUPERACAO))
                .thenReturn(Optional.empty());

        TokenValidacaoResponse response = authService.validarTokenRecuperacao(TOKEN_ATIVACAO);

        // 200 com valido=false, nao excecao: link invalido e desfecho esperado da sonda.
        assertThat(response.valido()).isFalse();
        assertThat(response.nome()).isNull();
    }

    @Test
    void validarTokenRecuperacao_ShouldNeverConsumeTheToken() {
        stubTokenRecuperacaoValido();

        authService.validarTokenRecuperacao(TOKEN_RECUPERACAO);
        authService.validarTokenRecuperacao(TOKEN_RECUPERACAO);

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
    }
}
