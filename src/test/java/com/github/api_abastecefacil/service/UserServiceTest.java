package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.dto.user.CreateUserRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.exception.DominioEmailNaoPermitidoException;
import com.github.api_abastecefacil.exception.EnvioEmailException;
import com.github.api_abastecefacil.exception.InvalidUserDataException;
import com.github.api_abastecefacil.exception.MatriculaDuplicadaException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.exception.RegionalNaoPermitidaException;
import com.github.api_abastecefacil.exception.SenhaJaDefinidaException;
import com.github.api_abastecefacil.exception.UserAlreadyDeletedException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.RegionalRepository;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.UserConstants.ROTA_DEFINIR_SENHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final List<String> DOMINIOS = List.of("fiesc.org.br", "sesisenai.org.br");

    private static final String FRONTEND_URL = "https://app.abastecefacil.com.br";
    private static final long ATIVACAO_HORAS = 48L;
    private static final String IP = "10.0.0.7";
    private static final String TOKEN = "Zm9vYmFyLXRva2VuLWRlLXRlc3Rl";

    private static final Long REGIONAL_JOI = 1L;
    private static final Long REGIONAL_FLN = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegionalRepository regionalRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private EnviadorEmail enviadorEmail;

    private UserService userService;

    private User user;
    private UserResponse userResponse;
    private Regional joinville;

    @BeforeEach
    void setUp() {
        // @InjectMocks nao serve: o construtor recebe a lista de dominios, que vem de
        // configuracao e e o proprio objeto de varios testes.
        userService = new UserService(
                userRepository, regionalRepository, userMapper, passwordEncoder,
                usuarioAutenticadoProvider, tokenAcessoService, enviadorEmail,
                DOMINIOS, FRONTEND_URL, ATIVACAO_HORAS);

        joinville = new Regional().setId(REGIONAL_JOI).setNome("Joinville").setSigla("JOI").setAtivo(true);

        user = new User()
                .setId(1L)
                .setName("User One")
                .setEmail("one@fiesc.org.br")
                .setPassword("pass123")
                .setActive(true)
                .setCreatedAt(LocalDateTime.now())
                .setPerfil(Perfil.COLABORADOR)
                .setSenhaDefinida(true);

        userResponse = new UserResponse(
                1L, "User One", "one@fiesc.org.br", true, LocalDateTime.now(), null,
                Perfil.COLABORADOR, null, "47999998888", "12345", false, null);
    }

    // ------------------------------------------------------------------ fixtures

    private User autor(Perfil perfil, Regional regional) {
        return new User().setId(99L).setEmail("autor@fiesc.org.br").setPerfil(perfil).setRegional(regional);
    }

    private CreateUserRequest pedido(Perfil perfil, Long regionalId, String matricula) {
        return new CreateUserRequest(
                "Novo Colaborador", "novo@fiesc.org.br", "(47) 99999-8888", matricula, perfil, regionalId);
    }

    private CreateUserRequest pedidoColaborador() {
        return pedido(Perfil.COLABORADOR, REGIONAL_JOI, "12345");
    }

    /** Encurta o caminho feliz, que precisa dos mesmos quatro stubs em vários testes. */
    private void stubCaminhoFeliz(CreateUserRequest request) {
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByMatricula(request.matricula())).thenReturn(false);
        when(regionalRepository.findById(request.regionalId())).thenReturn(Optional.of(joinville));
        when(userMapper.toEntity(eq(request), any())).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(eq(user), any())).thenReturn(userResponse);
    }

    // ------------------------------------------------------- criacao: autorizacao

    @Test
    void createUser_ShouldCreateColaborador_WhenGestorActsOnOwnRegional() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        stubCaminhoFeliz(request);

        UserResponse response = userService.createUser(request, IP);

        assertThat(response).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void createUser_ShouldThrowRegionalNaoPermitida_WhenGestorActsOnAnotherRegional() {
        CreateUserRequest request = pedido(Perfil.COLABORADOR, REGIONAL_FLN, "12345");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));

        assertThrows(RegionalNaoPermitidaException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldThrowPerfilNaoPermitido_WhenGestorCreatesAdministrador() {
        CreateUserRequest request = pedido(Perfil.ADMINISTRADOR, REGIONAL_JOI, "12345");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldThrowPerfilNaoPermitido_WhenGestorCreatesAnotherGestor() {
        CreateUserRequest request = pedido(Perfil.GESTOR_FROTA, REGIONAL_JOI, "12345");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldThrowRegionalNaoPermitida_WhenGestorHasNoRegional() {
        // Sem "propria regional" com que comparar, o caminho permissivo deixaria o gestor
        // criar em qualquer lugar.
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, null));

        assertThrows(RegionalNaoPermitidaException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldThrowPerfilNaoPermitido_WhenAuthorIsColaborador() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.COLABORADOR, joinville));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldAllowAdministrador_ToCreateAnyPerfilOnAnyRegional() {
        CreateUserRequest request = pedido(Perfil.GESTOR_FROTA, REGIONAL_FLN, "12345");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, joinville));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByMatricula("12345")).thenReturn(false);
        when(regionalRepository.findById(REGIONAL_FLN)).thenReturn(Optional.of(joinville));
        when(userMapper.toEntity(eq(request), any())).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(eq(user), any())).thenReturn(userResponse);

        assertThat(userService.createUser(request, IP)).isNotNull();
    }

    @Test
    void createUser_ShouldAuthorizeBeforeValidating() {
        // Um gestor tentando criar administrador leva 403 mesmo com o payload todo
        // errado. Se validasse antes, a mensagem de erro revelaria o que mais estava
        // errado a quem nem podia criar o usuario.
        CreateUserRequest request = new CreateUserRequest(
                "X", "fora@gmail.com", "telefone-invalido", null, Perfil.ADMINISTRADOR, REGIONAL_FLN);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.createUser(request, IP));
    }

    // ------------------------------------------------------- criacao: validacao

    @Test
    void createUser_ShouldThrowInvalidUserData_WhenColaboradorHasNoMatricula() {
        CreateUserRequest request = pedido(Perfil.COLABORADOR, REGIONAL_JOI, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(InvalidUserDataException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldThrowInvalidUserData_WhenGestorHasNoMatricula() {
        CreateUserRequest request = pedido(Perfil.GESTOR_FROTA, REGIONAL_JOI, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(InvalidUserDataException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldAcceptAdministradorWithoutMatriculaAndRegional() {
        // Conta de infraestrutura pode nao pertencer a regional nenhuma, como o
        // administrador inicial criado pelo A3.
        CreateUserRequest request = new CreateUserRequest(
                "Admin Novo", "admin@fiesc.org.br", null, null, Perfil.ADMINISTRADOR, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userMapper.toEntity(request, null)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(eq(user), any())).thenReturn(userResponse);

        assertThat(userService.createUser(request, IP)).isNotNull();
        verify(regionalRepository, never()).findById(any());
    }

    @Test
    void createUser_ShouldThrowInvalidUserData_WhenColaboradorHasNoRegional() {
        CreateUserRequest request = pedido(Perfil.COLABORADOR, null, "12345");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(InvalidUserDataException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldRejectEmailOutsideAllowedDomains() {
        CreateUserRequest request = new CreateUserRequest(
                "Fulano", "fulano@gmail.com", null, "12345", Perfil.COLABORADOR, REGIONAL_JOI);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(DominioEmailNaoPermitidoException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldRejectAllowedDomainUsedAsLocalPart() {
        // fiesc.org.br@gmail.com: o dominio permitido aparece como nome de usuario. Um
        // contains sobre o e-mail inteiro aceitaria, e o endereco e do Gmail.
        CreateUserRequest request = new CreateUserRequest(
                "Fulano", "fiesc.org.br@gmail.com", null, "12345", Perfil.COLABORADOR, REGIONAL_JOI);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(DominioEmailNaoPermitidoException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldRejectAllowedDomainUsedAsLabelInAnotherDomain() {
        // contato@fiesc.org.br.exemplo.com: o dominio real e exemplo.com.
        CreateUserRequest request = new CreateUserRequest(
                "Fulano", "contato@fiesc.org.br.exemplo.com", null, "12345", Perfil.COLABORADOR, REGIONAL_JOI);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(DominioEmailNaoPermitidoException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldThrowInvalidUserData_WhenTelefoneIsMalformed() {
        CreateUserRequest request = new CreateUserRequest(
                "Fulano", "fulano@fiesc.org.br", "+55 47 99999-8888", "12345", Perfil.COLABORADOR, REGIONAL_JOI);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(InvalidUserDataException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldThrowInvalidUserData_WhenMatriculaIsMalformed() {
        CreateUserRequest request = pedido(Perfil.COLABORADOR, REGIONAL_JOI, "12A4");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));

        assertThrows(InvalidUserDataException.class, () -> userService.createUser(request, IP));
    }

    // ------------------------------------------------------- criacao: unicidade

    @Test
    void createUser_ShouldThrowUserAlreadyExistsException_WhenEmailExists() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.existsByEmail("novo@fiesc.org.br")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldThrowMatriculaDuplicada_WhenMatriculaExists() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByMatricula("12345")).thenReturn(true);

        assertThrows(MatriculaDuplicadaException.class, () -> userService.createUser(request, IP));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ShouldTranslateConstraintViolationOnMatricula_ToMatriculaDuplicada() {
        // Backstop de corrida: existsBy... seguido de save tem janela entre ler e
        // escrever, e a constraint do banco e a garantia real. Sem o catch isso seria um
        // 500 cru, porque DataIntegrityViolationException nao tem handler.
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByMatricula("12345")).thenReturn(false);
        when(regionalRepository.findById(REGIONAL_JOI)).thenReturn(Optional.of(joinville));
        when(userMapper.toEntity(eq(request), any())).thenReturn(user);
        when(userRepository.save(user))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique index \"uk_users_matricula\""));

        assertThrows(MatriculaDuplicadaException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldTranslateConstraintViolationOnEmail_ToUserAlreadyExists() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByMatricula("12345")).thenReturn(false);
        when(regionalRepository.findById(REGIONAL_JOI)).thenReturn(Optional.of(joinville));
        when(userMapper.toEntity(eq(request), any())).thenReturn(user);
        when(userRepository.save(user))
                .thenThrow(new DataIntegrityViolationException("uk6dotkott2kjsp8vw4d0m25fb7"));

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(request, IP));
    }

    @Test
    void createUser_ShouldThrowNotFound_WhenRegionalDoesNotExist() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByMatricula("12345")).thenReturn(false);
        when(regionalRepository.findById(REGIONAL_JOI)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.createUser(request, IP));
    }

    // ------------------------------------------------- criacao: usuario sem senha

    @Test
    void createUser_ShouldNeverTouchThePasswordEncoder() {
        // Nenhum administrador escolhe a senha de outra pessoa. Nao ha o que codificar:
        // o usuario nasce sem senha e a define pelo convite do S2b1.
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);

        userService.createUser(request, IP);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createUser_ShouldPassTheResolvedRegionalToTheMapper() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);

        userService.createUser(request, IP);

        ArgumentCaptor<Regional> captor = ArgumentCaptor.forClass(Regional.class);
        verify(userMapper).toEntity(eq(request), captor.capture());
        assertThat(captor.getValue()).isSameAs(joinville);
    }

    // ------------------------------------------------------- convite de ativacao

    private User alvoPendente() {
        return new User().setId(5L).setName("Convidada").setEmail("convidada@fiesc.org.br")
                .setPerfil(Perfil.COLABORADOR).setRegional(joinville)
                .setActive(true).setSenhaDefinida(false).setPassword(null);
    }

    @Test
    void createUser_ShouldIssueAnAtivacaoTokenAndSendTheInvite() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);
        when(tokenAcessoService.gerarToken(user.getEmail(), FinalidadeToken.ATIVACAO, IP)).thenReturn(TOKEN);

        userService.createUser(request, IP);

        ArgumentCaptor<MensagemAcesso> captor = ArgumentCaptor.forClass(MensagemAcesso.class);
        verify(enviadorEmail).enviar(captor.capture());

        MensagemAcesso enviada = captor.getValue();
        assertThat(enviada.destinatario()).isEqualTo(user.getEmail());
        assertThat(enviada.nomeDestinatario()).isEqualTo(user.getName());
        assertThat(enviada.finalidade()).isEqualTo(FinalidadeToken.ATIVACAO);
        // A validade exibida tem que ser a configurada, nao uma constante do corpo.
        assertThat(enviada.validadeHoras()).isEqualTo(ATIVACAO_HORAS);
    }

    @Test
    void createUser_ShouldBuildTheLinkOverTheConfiguredFrontendUrl() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        userService.createUser(request, IP);

        ArgumentCaptor<MensagemAcesso> captor = ArgumentCaptor.forClass(MensagemAcesso.class);
        verify(enviadorEmail).enviar(captor.capture());

        assertThat(captor.getValue().urlAcao())
                .isEqualTo(FRONTEND_URL + ROTA_DEFINIR_SENHA + TOKEN);
    }

    @Test
    void createUser_ShouldNotDoubleTheSlash_WhenFrontendUrlEndsWithOne() {
        // Escrever a base com barra final e natural em configuracao.
        UserService comBarra = new UserService(
                userRepository, regionalRepository, userMapper, passwordEncoder,
                usuarioAutenticadoProvider, tokenAcessoService, enviadorEmail,
                DOMINIOS, FRONTEND_URL + "/", ATIVACAO_HORAS);

        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        comBarra.createUser(request, IP);

        ArgumentCaptor<MensagemAcesso> captor = ArgumentCaptor.forClass(MensagemAcesso.class);
        verify(enviadorEmail).enviar(captor.capture());

        assertThat(captor.getValue().urlAcao()).doesNotContain("//definir-senha");
    }

    @Test
    void createUser_ShouldReportConviteEnviadoTrue_OnSuccess() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);

        userService.createUser(request, IP);

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(userMapper).toResponse(eq(user), captor.capture());
        assertThat(captor.getValue()).isTrue();
    }

    @Test
    void createUser_ShouldStillCreateTheUser_WhenTheInviteFailsToSend() {
        // A regra central da transacionalidade escolhida: falha de envio nunca derruba a
        // criacao. O usuario existe, e o conserto e o reenvio.
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);
        doThrow(new EnvioEmailException("provedor fora do ar")).when(enviadorEmail).enviar(any());

        assertThatCode(() -> userService.createUser(request, IP)).doesNotThrowAnyException();

        verify(userRepository).save(user);
        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(userMapper).toResponse(eq(user), captor.capture());
        assertThat(captor.getValue()).isFalse();
    }

    @Test
    void createUser_ShouldNeverLogTheActivationLink() {
        // A URL carrega o token em claro. So o EnviadorEmailLog pode registra-la.
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UserService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CreateUserRequest request = pedidoColaborador();
            when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                    .thenReturn(autor(Perfil.ADMINISTRADOR, null));
            stubCaminhoFeliz(request);
            when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);
            doThrow(new EnvioEmailException("falhou")).when(enviadorEmail).enviar(any());

            userService.createUser(request, IP);

            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage))
                    .noneMatch(m -> m.contains(TOKEN));
            // O ERROR existe: o campo avisa o gestor, o log permite investigar depois.
            assertThat(appender.list).anyMatch(e -> e.getLevel().equals(ch.qos.logback.classic.Level.ERROR));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ------------------------------------------------------------ reenvio

    @Test
    void reenviarAtivacao_ShouldIssueANewTokenAndSend() {
        User alvo = alvoPendente();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));
        when(tokenAcessoService.gerarToken(alvo.getEmail(), FinalidadeToken.ATIVACAO, IP)).thenReturn(TOKEN);
        when(userMapper.toResponse(eq(alvo), any())).thenReturn(userResponse);

        userService.reenviarAtivacao(5L, IP);

        // Gerar token novo invalida o anterior -- garantia do M2, no repository.
        verify(tokenAcessoService).gerarToken(alvo.getEmail(), FinalidadeToken.ATIVACAO, IP);
        verify(enviadorEmail).enviar(any(MensagemAcesso.class));
    }

    @Test
    void reenviarAtivacao_ShouldThrowSenhaJaDefinida_WhenUserAlreadyHasPassword() {
        User jaAtivo = alvoPendente().setSenhaDefinida(true).setPassword("hash");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(5L)).thenReturn(Optional.of(jaAtivo));

        assertThrows(SenhaJaDefinidaException.class, () -> userService.reenviarAtivacao(5L, IP));

        verifyNoInteractions(tokenAcessoService, enviadorEmail);
    }

    @Test
    void reenviarAtivacao_ShouldApplyTheSameAuthorizationRulesAsCreation() {
        User alvoOutraRegional = alvoPendente()
                .setRegional(new Regional().setId(REGIONAL_FLN).setSigla("FLN"));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvoOutraRegional));

        assertThrows(RegionalNaoPermitidaException.class, () -> userService.reenviarAtivacao(5L, IP));

        verifyNoInteractions(tokenAcessoService, enviadorEmail);
    }

    @Test
    void reenviarAtivacao_ShouldThrowPerfilNaoPermitido_WhenGestorTargetsAnAdministrador() {
        User admin = alvoPendente().setPerfil(Perfil.ADMINISTRADOR);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(admin));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.reenviarAtivacao(5L, IP));
    }

    @Test
    void reenviarAtivacao_ShouldAllowGestor_OnOwnRegionalColaborador() {
        User alvo = alvoPendente();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));
        when(userMapper.toResponse(eq(alvo), any())).thenReturn(userResponse);

        assertThat(userService.reenviarAtivacao(5L, IP)).isNotNull();
        verify(enviadorEmail).enviar(any(MensagemAcesso.class));
    }

    @Test
    void reenviarAtivacao_ShouldThrowNotFound_WhenUserDoesNotExist() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.reenviarAtivacao(404L, IP));
    }

    @Test
    void reenviarAtivacao_ShouldReportConviteEnviadoFalse_WhenSendingFails() {
        User alvo = alvoPendente();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));
        when(userMapper.toResponse(eq(alvo), any())).thenReturn(userResponse);
        doThrow(new EnvioEmailException("falhou")).when(enviadorEmail).enviar(any());

        assertThatCode(() -> userService.reenviarAtivacao(5L, IP)).doesNotThrowAnyException();

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(userMapper).toResponse(eq(alvo), captor.capture());
        assertThat(captor.getValue()).isFalse();
    }

    // ------------------------------------------------------------ demais fluxos

    @Test
    void getUserById_ShouldReturnUser_WhenUserExistsAndIsActive() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.getUserById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getUserById_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void getUserById_ShouldThrowUserAlreadyDeletedException_WhenUserIsInactive() {
        user.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(UserAlreadyDeletedException.class, () -> userService.getUserById(1L));
    }

    @Test
    void updateUser_ShouldUpdateUserSuccessfully() {
        UpdateUserRequest request = new UpdateUserRequest("Updated Name", "new@test.com", "newpass");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("encodedNewPass");
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.updateUser(1L, request);

        assertThat(response).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_ShouldDeactivateUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        assertThat(user.getActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void getAllUsers_ShouldReturnPagedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user));

        when(userRepository.findByIsActiveAndNameContainingIgnoreCase(true, "User", pageable)).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(true, "User", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void countAllActiveUsers_ShouldReturnCount() {
        when(userRepository.countByIsActiveTrue()).thenReturn(5L);

        Long count = userService.countAllActiveUsers();

        assertThat(count).isEqualTo(5L);
    }
}
