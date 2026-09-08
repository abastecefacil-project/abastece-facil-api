package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.regional.RegionalSummaryResponse;
import com.github.api_abastecefacil.dto.user.CreateUserRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.exception.AutoExclusaoNaoPermitidaException;
import com.github.api_abastecefacil.exception.DominioEmailNaoPermitidoException;
import com.github.api_abastecefacil.exception.InvalidUserDataException;
import com.github.api_abastecefacil.exception.MatriculaDuplicadaException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.exception.RegionalNaoPermitidaException;
import com.github.api_abastecefacil.exception.SenhaDeTerceiroException;
import com.github.api_abastecefacil.exception.SenhaFracaException;
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

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final List<String> DOMINIOS = List.of("fiesc.org.br", "sesisenai.org.br");

    private static final String IP = "10.0.0.7";

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
    private EnvioAcessoService envioAcessoService;

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
                usuarioAutenticadoProvider, envioAcessoService, DOMINIOS);

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
        return autorComId(99L, perfil, regional);
    }

    /**
     * Autor com id escolhido. O P0.4c precisa distinguir "o autor e o alvo" de "o autor e
     * outra pessoa", e a comparacao no service e por id.
     */
    private User autorComId(Long id, Perfil perfil, Regional regional) {
        return new User().setId(id).setName("Autor Um").setEmail("autor@fiesc.org.br")
                .setPerfil(perfil).setRegional(regional).setActive(true);
    }

    private Regional florianopolis() {
        return new Regional().setId(REGIONAL_FLN).setNome("Florianópolis").setSigla("FLN").setAtivo(true);
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

    /**
     * A montagem da mensagem, do link e do prazo saiu daqui no S4: ela e a mesma da
     * recuperacao de senha e passou a viver no EnvioAcessoService, que tem teste proprio.
     * O que cabe ao UserService provar e a delegacao -- que o cadastro dispara o envio,
     * para o usuario recem-criado, com a finalidade de ATIVACAO e o IP do solicitante.
     */
    @Test
    void createUser_ShouldDelegateTheInvite_WithAtivacaoFinalidade() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);

        userService.createUser(request, IP);

        verify(envioAcessoService).enviar(user, FinalidadeToken.ATIVACAO, IP);
    }

    @Test
    void createUser_ShouldReportConviteEnviadoTrue_OnSuccess() {
        CreateUserRequest request = pedidoColaborador();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        stubCaminhoFeliz(request);
        when(envioAcessoService.enviar(user, FinalidadeToken.ATIVACAO, IP)).thenReturn(true);

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
        when(envioAcessoService.enviar(user, FinalidadeToken.ATIVACAO, IP)).thenReturn(false);

        assertThatCode(() -> userService.createUser(request, IP)).doesNotThrowAnyException();

        verify(userRepository).save(user);
        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(userMapper).toResponse(eq(user), captor.capture());
        assertThat(captor.getValue()).isFalse();
    }

    // O teste de segredo do log -- "nada aqui registra a URL, que carrega o token em
    // claro" -- migrou para o EnvioAcessoServiceTest junto com o codigo que ele guarda.
    // O UserService nao tem mais logger: ele nao emite token nem envia e-mail.

    // ------------------------------------------------------------ reenvio

    @Test
    void reenviarAtivacao_ShouldIssueANewTokenAndSend() {
        User alvo = alvoPendente();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));
        when(userMapper.toResponse(eq(alvo), any())).thenReturn(userResponse);

        userService.reenviarAtivacao(5L, IP);

        // Gerar token novo invalida o anterior -- garantia do M2, no repository.
        verify(envioAcessoService).enviar(alvo, FinalidadeToken.ATIVACAO, IP);
    }

    @Test
    void reenviarAtivacao_ShouldThrowSenhaJaDefinida_WhenUserAlreadyHasPassword() {
        User jaAtivo = alvoPendente().setSenhaDefinida(true).setPassword("hash");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(5L)).thenReturn(Optional.of(jaAtivo));

        assertThrows(SenhaJaDefinidaException.class, () -> userService.reenviarAtivacao(5L, IP));

        verifyNoInteractions(envioAcessoService);
    }

    @Test
    void reenviarAtivacao_ShouldApplyTheSameAuthorizationRulesAsCreation() {
        User alvoOutraRegional = alvoPendente()
                .setRegional(new Regional().setId(REGIONAL_FLN).setSigla("FLN"));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvoOutraRegional));

        assertThrows(RegionalNaoPermitidaException.class, () -> userService.reenviarAtivacao(5L, IP));

        verifyNoInteractions(envioAcessoService);
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
        verify(envioAcessoService).enviar(alvo, FinalidadeToken.ATIVACAO, IP);
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
        when(envioAcessoService.enviar(alvo, FinalidadeToken.ATIVACAO, IP)).thenReturn(false);

        assertThatCode(() -> userService.reenviarAtivacao(5L, IP)).doesNotThrowAnyException();

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(userMapper).toResponse(eq(alvo), captor.capture());
        assertThat(captor.getValue()).isFalse();
    }

    // ------------------------------------------------------------ demais fluxos

    @Test
    void getUserById_ShouldReturnUser_WhenUserExistsAndIsActive() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.getUserById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getUserById_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void getUserById_ShouldThrowUserAlreadyDeletedException_WhenUserIsInactive() {
        user.setActive(false);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(UserAlreadyDeletedException.class, () -> userService.getUserById(1L));
    }

    @Test
    void updateUser_ShouldUpdateUserSuccessfully() {
        // A senha e do proprio autor, unico caso que o PATCH aceita desde o P0.4c, e
        // passa pela politica do S3 -- 10 caracteres, letra e digito, sem nome nem e-mail.
        UpdateUserRequest request = new UpdateUserRequest("Updated Name", "nova@test.com", "Chuva2026Forte");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autorComId(1L, Perfil.COLABORADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("nova@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Chuva2026Forte")).thenReturn("encodedNewPass");
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.updateUser(1L, request);

        assertThat(response).isNotNull();
        assertThat(user.getPassword()).isEqualTo("encodedNewPass");
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_ShouldDeactivateUser() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        assertThat(user.getActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void getAllUsers_ShouldReturnPagedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user));

        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findByIsActiveAndNameContainingIgnoreCase(true, "User", pageable)).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(true, "User", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    // ------------------------------------------------ P0.5b: GET /api/users/me

    @Test
    void getUsuarioAutenticado_ShouldReturnOwnRecord_WithRegional() {
        User gestor = autorComId(9L, Perfil.GESTOR_FROTA, joinville);
        UserResponse esperado = new UserResponse(
                9L, "Autor Um", "autor@fiesc.org.br", true, LocalDateTime.now(), null,
                Perfil.GESTOR_FROTA, new RegionalSummaryResponse(REGIONAL_JOI, "Joinville", "JOI"),
                null, null, true, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(gestor);
        when(userMapper.toResponse(gestor)).thenReturn(esperado);

        UserResponse response = userService.getUsuarioAutenticado();

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.regional().sigla()).isEqualTo("JOI");
    }

    @Test
    void getUsuarioAutenticado_ShouldReturnOwnRecord_WhenColaborador() {
        User colaborador = autorComId(1L, Perfil.COLABORADOR, joinville);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(colaborador);
        when(userMapper.toResponse(colaborador)).thenReturn(userResponse);

        assertThat(userService.getUsuarioAutenticado()).isSameAs(userResponse);
    }

    @Test
    void getUsuarioAutenticado_ShouldNotLookUpAnyUserById() {
        // O alvo e o proprio autor: nao ha id de entrada, nao ha busca e nao ha
        // autorizacao a aplicar. Se algum dia isso passar por buscarUsuarioPorId, o
        // endpoint deixou de ser "eu mesmo".
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autorComId(1L, Perfil.COLABORADOR, null));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        userService.getUsuarioAutenticado();

        verify(userRepository, never()).findById(any());
    }

    // -------------------------------------------------- P0.4c: leitura de usuario

    @Test
    void getUserById_ShouldAllowColaborador_ToReadOwnRecord() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autorComId(1L, Perfil.COLABORADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        assertThat(userService.getUserById(1L)).isNotNull();
    }

    @Test
    void getUserById_ShouldThrowPerfilNaoPermitido_WhenColaboradorReadsAnotherUser() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.COLABORADOR, joinville));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.getUserById(1L));

        verifyNoInteractions(userMapper);
    }

    @Test
    void getUserById_ShouldAllowGestor_OnOwnRegional() {
        User alvo = alvoPendente();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));
        when(userMapper.toResponse(alvo)).thenReturn(userResponse);

        assertThat(userService.getUserById(5L)).isNotNull();
    }

    @Test
    void getUserById_ShouldThrowRegionalNaoPermitida_WhenGestorReadsAnotherRegional() {
        User alvo = alvoPendente().setRegional(florianopolis());
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));

        assertThrows(RegionalNaoPermitidaException.class, () -> userService.getUserById(5L));
    }

    @Test
    void getUserById_ShouldAuthorizeBeforeReportingThatTheUserIsDeleted() {
        // Autorizar vem antes de validar. Responder "usuario deletado" a quem nem podia
        // ve-lo confirmaria a existencia da conta e o seu estado.
        user.setActive(false);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.COLABORADOR, joinville));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.getUserById(1L));
    }

    // -------------------------------------------------- P0.4c: edicao de usuario

    @Test
    void updateUser_ShouldThrowPerfilNaoPermitido_WhenColaboradorUpdatesAnotherUser() {
        UpdateUserRequest request = new UpdateUserRequest("Invadido", null, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.COLABORADOR, joinville));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.updateUser(1L, request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_ShouldAllowGestor_OnOwnRegionalColaborador() {
        User alvo = alvoPendente();
        UpdateUserRequest request = new UpdateUserRequest("Nome Corrigido", null, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));
        when(userRepository.save(alvo)).thenReturn(alvo);
        when(userMapper.toResponse(alvo)).thenReturn(userResponse);

        assertThat(userService.updateUser(5L, request)).isNotNull();
        assertThat(alvo.getName()).isEqualTo("Nome Corrigido");
    }

    @Test
    void updateUser_ShouldThrowPerfilNaoPermitido_WhenGestorUpdatesAnotherGestor() {
        // A escrita nao pode ser mais permissiva que a criacao: o S2a ja impede um gestor
        // de criar outro gestor. Sem esta regra, dois gestores da mesma regional poderiam
        // editar um ao outro -- escalacao lateral.
        User alvo = alvoPendente().setPerfil(Perfil.GESTOR_FROTA);
        UpdateUserRequest request = new UpdateUserRequest("Nome", null, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.updateUser(5L, request));
    }

    @Test
    void updateUser_ShouldThrowRegionalNaoPermitida_WhenGestorUpdatesAnotherRegional() {
        User alvo = alvoPendente().setRegional(florianopolis());
        UpdateUserRequest request = new UpdateUserRequest("Nome", null, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));

        assertThrows(RegionalNaoPermitidaException.class, () -> userService.updateUser(5L, request));
    }

    @Test
    void updateUser_ShouldThrowSenhaDeTerceiro_WhenAdministradorSetsAnotherUsersPassword() {
        // Era por aqui que qualquer autenticado assumia a conta de um administrador. Nem
        // o administrador escolhe a senha de outra pessoa: quem esqueceu usa a recuperacao.
        UpdateUserRequest request = new UpdateUserRequest(null, null, "Chuva2026Forte");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.ADMINISTRADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(SenhaDeTerceiroException.class, () -> userService.updateUser(1L, request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateUser_ShouldThrowSenhaFraca_WhenTheOwnPasswordViolatesThePolicy() {
        // O PATCH era um segundo caminho para senha fraca, depois de o S3 ter fechado o
        // da ativacao.
        UpdateUserRequest request = new UpdateUserRequest(null, null, "123");
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autorComId(1L, Perfil.COLABORADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(SenhaFracaException.class, () -> userService.updateUser(1L, request));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateUser_ShouldNotTreatTheOwnCurrentEmailAsDuplicate() {
        UpdateUserRequest request = new UpdateUserRequest("Nome Novo", "one@fiesc.org.br", null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autorComId(1L, Perfil.COLABORADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        assertThat(userService.updateUser(1L, request)).isNotNull();

        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateUserRequest_ShouldNotCarryPerfilNorRegional() {
        // Criterio de aceite do P0.4c: ninguem altera perfil nem regional pelo PATCH, o
        // proprio inclusive. Nao ha checagem no service porque nao ha campo -- guardar
        // contra um campo inexistente seria codigo morto sugerindo o contrario. Este
        // teste guarda a premissa: acrescentar os campos ao DTO quebra aqui e obriga a
        // decidir a autorizacao junto.
        assertThat(UpdateUserRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("name", "email", "password");
    }

    // ------------------------------------------------- P0.4c: exclusao de usuario

    @Test
    void deleteUser_ShouldThrowPerfilNaoPermitido_WhenAuthorIsColaborador() {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.COLABORADOR, joinville));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.deleteUser(1L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_ShouldThrowPerfilNaoPermitido_WhenAuthorIsGestor() {
        User alvo = alvoPendente();
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alvo));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.deleteUser(5L));
    }

    @Test
    void deleteUser_ShouldThrowAutoExclusaoNaoPermitida_WhenAdministradorDeletesHimself() {
        // A exclusao e logica, e desde o S3 usuario inativo deixa de autenticar: o
        // administrador perderia o proprio acesso na hora, sem endpoint que o reative.
        User admin = autorComId(1L, Perfil.ADMINISTRADOR, null);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(admin);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AutoExclusaoNaoPermitidaException.class, () -> userService.deleteUser(1L));

        assertThat(user.getActive()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_ShouldRejectSelfDeletion_ByPerfil_WhenAuthorIsNotAdministrador() {
        // O perfil e conferido antes do autosservico: dizer "voce nao pode excluir a
        // propria conta" a um colaborador daria a entender que ele poderia excluir outra.
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autorComId(1L, Perfil.COLABORADOR, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(PerfilNaoPermitidoException.class, () -> userService.deleteUser(1L));

        assertThat(user.getActive()).isTrue();
    }

    // ------------------------------------------------- P0.4c: escopo da listagem

    @Test
    void getAllUsers_ShouldScopeToOwnRegional_WhenAuthorIsGestor() {
        Pageable pageable = PageRequest.of(0, 10);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, joinville));
        when(userRepository.findByIsActiveAndRegionalIdAndNameContainingIgnoreCase(
                true, REGIONAL_JOI, "", pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.getAllUsers(true, "", pageable);

        verify(userRepository, never()).findByIsActiveAndNameContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void getAllUsers_ShouldScopeToSelf_WhenAuthorIsColaborador() {
        // Sem isto a restricao do GET /{id} seria contornavel em uma requisicao: o mesmo
        // dado sairia pela listagem.
        Pageable pageable = PageRequest.of(0, 10);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.COLABORADOR, joinville));
        when(userRepository.findByIsActiveAndIdAndNameContainingIgnoreCase(
                true, 99L, "", pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.getAllUsers(true, "", pageable);

        verify(userRepository, never()).findByIsActiveAndNameContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void getAllUsers_ShouldScopeToSelf_WhenGestorHasNoRegional() {
        // Gestor sem regional nao tem escopo com que filtrar, e o caminho permissivo
        // seria mostrar-lhe o sistema inteiro. Mesma postura do autorizarSobreUsuario.
        Pageable pageable = PageRequest.of(0, 10);
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(autor(Perfil.GESTOR_FROTA, null));
        when(userRepository.findByIsActiveAndIdAndNameContainingIgnoreCase(
                true, 99L, "", pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.getAllUsers(true, "", pageable);

        verify(userRepository, never()).findByIsActiveAndRegionalIdAndNameContainingIgnoreCase(
                any(), any(), any(), any());
    }

    @Test
    void countAllActiveUsers_ShouldReturnCount() {
        when(userRepository.countByIsActiveTrue()).thenReturn(5L);

        Long count = userService.countAllActiveUsers();

        assertThat(count).isEqualTo(5L);
    }
}
