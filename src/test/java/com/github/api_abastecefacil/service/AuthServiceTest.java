package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.AtivacaoRequest;
import com.github.api_abastecefacil.dto.auth.AtivacaoValidacaoResponse;
import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.exception.InvalidLoginException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PasswordNotSetException;
import com.github.api_abastecefacil.exception.SenhaFracaException;
import com.github.api_abastecefacil.exception.TokenInvalidoException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
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

import static com.github.api_abastecefacil.constants.TokenAcessoConstants.TOKEN_INVALIDO_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long JWT_EXPIRACAO = 86_400_000L;            // 24h
    private static final long JWT_EXPIRACAO_COLABORADOR = 2_592_000_000L; // 30 dias

    private static final String TOKEN_ATIVACAO = "token-de-ativacao-em-claro";
    private static final String SENHA_BOA = "Chuva8Verde";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

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
                userRepository, userMapper, jwtService, authenticationManager,
                customUserDetailsService, tokenAcessoService, passwordEncoder,
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

    // ------------------------------------------------------------------ register

    @Test
    void register_ShouldRegisterUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("Test User", "user@test.com", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.type()).isEqualTo("Bearer");
        assertThat(response.perfil()).isEqualTo(Perfil.COLABORADOR);
        verify(userRepository).save(user);
    }

    @Test
    void register_ShouldThrowUserAlreadyExistsException_WhenEmailExists() {
        RegisterRequest request = new RegisterRequest("Test User", "user@test.com", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
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

        AtivacaoValidacaoResponse response = authService.validarTokenAtivacao(TOKEN_ATIVACAO);

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

        AtivacaoValidacaoResponse response = authService.validarTokenAtivacao("qualquer");

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

        AuthResponse response = authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, SENHA_BOA));

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

        authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, SENHA_BOA));

        verify(tokenAcessoService).validarEConsumir(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO);
    }

    @Test
    void ativarConta_ShouldNotConsumeTheToken_WhenPasswordIsWeak() {
        // A razao da ordem escolhida: consumir antes de validar queimaria o link a cada
        // senha fraca, e a pessoa teria de pedir reenvio para tentar de novo.
        stubTokenValido();

        assertThrows(SenhaFracaException.class,
                () -> authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, "curta1")));

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void ativarConta_ShouldRejectPasswordContainingTheUserName() {
        stubTokenValido();

        assertThrows(SenhaFracaException.class,
                () -> authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, "Test2026Segura")));

        verify(tokenAcessoService, never()).validarEConsumir(anyString(), any());
    }

    @Test
    void ativarConta_ShouldThrowTokenInvalido_WhenTokenIsNotValid() {
        when(tokenAcessoService.emailDeTokenValido(anyString(), any())).thenReturn(Optional.empty());

        TokenInvalidoException ex = assertThrows(TokenInvalidoException.class,
                () -> authService.ativarConta(new AtivacaoRequest("qualquer", SENHA_BOA)));

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
                () -> authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, SENHA_BOA)));
    }

    @Test
    void ativarConta_ShouldThrowTokenInvalido_WhenUserIsInactive() {
        user.setActive(false);
        when(tokenAcessoService.emailDeTokenValido(TOKEN_ATIVACAO, FinalidadeToken.ATIVACAO))
                .thenReturn(Optional.of("user@test.com"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        // E nao o 401 de usuario inativo do login: aqui o interlocutor segura um link.
        assertThrows(TokenInvalidoException.class,
                () -> authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, SENHA_BOA)));
    }

    @Test
    void ativarConta_ShouldIssueLongSession_ForColaborador() {
        stubTokenValido();
        when(passwordEncoder.encode(SENHA_BOA)).thenReturn("hash-bcrypt");
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class), anyLong())).thenReturn("jwt-token");

        authService.ativarConta(new AtivacaoRequest(TOKEN_ATIVACAO, SENHA_BOA));

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(jwtService).generateToken(anyMap(), any(UserDetails.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(JWT_EXPIRACAO_COLABORADOR);
    }
}
