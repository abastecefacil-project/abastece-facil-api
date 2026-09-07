package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.dto.user.CreateUserRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.exception.AutoExclusaoNaoPermitidaException;
import com.github.api_abastecefacil.exception.EnvioEmailException;
import com.github.api_abastecefacil.exception.InvalidUserDataException;
import com.github.api_abastecefacil.exception.MatriculaDuplicadaException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.exception.RegionalNaoPermitidaException;
import com.github.api_abastecefacil.exception.SenhaDeTerceiroException;
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
import com.github.api_abastecefacil.validation.UserValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

import static com.github.api_abastecefacil.constants.RegionalConstants.REGIONAL_NOT_FOUND_MESSAGE;
import static com.github.api_abastecefacil.constants.UserConstants.*;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RegionalRepository regionalRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final TokenAcessoService tokenAcessoService;
    private final EnviadorEmail enviadorEmail;
    private final List<String> dominiosPermitidos;
    private final String frontendUrl;
    private final long ativacaoHoras;

    public UserService(
            UserRepository userRepository,
            RegionalRepository regionalRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            UsuarioAutenticadoProvider usuarioAutenticadoProvider,
            TokenAcessoService tokenAcessoService,
            EnviadorEmail enviadorEmail,
            @Value("${abastecefacil.auth.dominios-permitidos:}") List<String> dominiosPermitidos,
            @Value("${abastecefacil.email.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${abastecefacil.token.ativacao-horas:48}") long ativacaoHoras) {
        this.userRepository = userRepository;
        this.regionalRepository = regionalRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.tokenAcessoService = tokenAcessoService;
        this.enviadorEmail = enviadorEmail;
        this.dominiosPermitidos = dominiosPermitidos;
        this.frontendUrl = frontendUrl;
        this.ativacaoHoras = ativacaoHoras;
    }

    /**
     * Cadastro administrativo. Cria o usuário <b>sem senha</b>, com
     * {@code senhaDefinida = false} e ativo — ele não consegue entrar até o convite do
     * S2b1, e o {@code AuthService} o barra com 401 {@code PASSWORD_NOT_SET} nesse
     * intervalo.
     *
     * <p>A ordem das etapas é deliberada: <b>autorizar antes de validar</b>. Quem não pode
     * criar o usuário não deve receber pistas sobre o payload — um gestor tentando criar
     * administrador leva 403 tanto com matrícula certa quanto errada, e não descobre pela
     * mensagem de erro qual das duas coisas o barrou.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request, String ipSolicitante) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        autorizarSobreUsuario(autor, request.perfil(), request.regionalId());

        UserValidator.validarDominioEmail(request.email(), dominiosPermitidos);
        UserValidator.validarTelefone(request.telefone());
        UserValidator.validarMatricula(request.matricula());
        validateCamposObrigatoriosDoPerfil(request);

        validateEmailDoesNotExist(request.email());
        validateMatriculaDoesNotExist(request.matricula());

        Regional regional = resolverRegional(request.regionalId());
        User savedUser = salvarComBackstop(userMapper.toEntity(request, regional));

        boolean conviteEnviado = enviarConviteAtivacao(savedUser, ipSolicitante);

        return userMapper.toResponse(savedUser, conviteEnviado);
    }

    /**
     * Reenvia o convite de ativação, com as <b>mesmas regras de autorização da criação</b>
     * — aplicadas ao perfil e à regional que o usuário-alvo tem hoje. Um gestor só
     * reenvia para colaborador da própria regional, pelo mesmo motivo por que só cria
     * assim: reenviar dispara um link que define a senha da conta, então é a mesma
     * capacidade.
     *
     * <p>Gerar o token novo invalida o anterior, garantia que já vem do M2 — dois links
     * válidos circulando para o mesmo par (e-mail, finalidade) seria o pior desfecho de um
     * reenvio.
     */
    @Transactional
    public UserResponse reenviarAtivacao(Long userId, String ipSolicitante) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        User alvo = findUserByIdOrThrow(userId);

        autorizarSobreUsuario(autor, alvo.getPerfil(),
                alvo.getRegional() == null ? null : alvo.getRegional().getId());

        // Reenviar para quem ja tem senha entregaria um link capaz de troca-la a quem
        // pediu o reenvio -- que nao e necessariamente o dono da conta. Quem esqueceu a
        // senha usa a recuperacao (S4), que exige acesso a caixa de e-mail.
        if (Boolean.TRUE.equals(alvo.getSenhaDefinida())) {
            throw new SenhaJaDefinidaException(SENHA_JA_DEFINIDA_MESSAGE);
        }

        boolean conviteEnviado = enviarConviteAtivacao(alvo, ipSolicitante);

        return userMapper.toResponse(alvo, conviteEnviado);
    }

    /**
     * Emite o token de ativação, monta o link e envia. Devolve se o e-mail saiu.
     *
     * <p><b>Falha de envio nunca derruba a criação.</b> A alternativa — tornar criação e
     * envio atômicos — é pior: enviar e-mail é irreversível, então se a mensagem saísse e
     * o commit falhasse, o convite apontaria para um usuário que não existe. Aqui o
     * usuário fica criado, a resposta traz {@code conviteEnviado = false} e o caminho de
     * conserto é {@code POST /api/users/{id}/reenviar-ativacao}.
     *
     * <p>O campo avisa o gestor que está na tela; o {@code ERROR} no log é o que permite
     * descobrir depois se a falha é sistemática — um gestor sozinho não distingue "o
     * Resend caiu agora" de "a chave está errada há dois dias".
     *
     * <p><b>Nada aqui loga a URL</b>, que carrega o token em claro. Só o
     * {@code EnviadorEmailLog} pode, e ele existe apenas para desenvolvimento.
     */
    private boolean enviarConviteAtivacao(User usuario, String ipSolicitante) {
        String token = tokenAcessoService.gerarToken(
                usuario.getEmail(), FinalidadeToken.ATIVACAO, ipSolicitante);

        MensagemAcesso mensagem = new MensagemAcesso(
                usuario.getEmail(),
                usuario.getName(),
                montarLinkAtivacao(token),
                FinalidadeToken.ATIVACAO,
                ativacaoHoras);

        try {
            enviadorEmail.enviar(mensagem);
        } catch (EnvioEmailException e) {
            log.error(CONVITE_FALHOU_LOG, usuario.getEmail(), usuario.getPerfil(), usuario.getId(), e);
            return false;
        }

        log.info(CONVITE_ENVIADO_LOG, usuario.getEmail(), usuario.getPerfil());
        return true;
    }

    /**
     * O link é a única coisa que o usuário convidado recebe, e a rota é contrato com o
     * frontend — ver {@link com.github.api_abastecefacil.constants.UserConstants#ROTA_DEFINIR_SENHA}.
     *
     * <p>A barra final da base é removida para não gerar {@code //definir-senha}: o valor
     * vem de configuração e escrever {@code http://localhost:5173/} é natural.
     */
    private String montarLinkAtivacao(String token) {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        return base + ROTA_DEFINIR_SENHA + token;
    }

    /**
     * ADMINISTRADOR cria qualquer perfil em qualquer regional. GESTOR_FROTA cria apenas
     * COLABORADOR e apenas na própria regional. Ninguém mais cria.
     *
     * <p>A regional do autor é lida <b>do banco</b>, via
     * {@code UsuarioAutenticadoProvider}, e não do JWT. O token não a carrega, e não
     * deveria: ele vive 24 horas, então alguém movido de regional continuaria autorizado
     * na antiga até o próximo login. Ver §6 do CLAUDE.md.
     */
    private void autorizarSobreUsuario(User autor, Perfil perfilAlvo, Long regionalIdAlvo) {
        autorizarSobreUsuario(autor, perfilAlvo, regionalIdAlvo,
                PERFIL_NAO_PERMITIDO_MESSAGE, REGIONAL_NAO_PERMITIDA_MESSAGE);
    }

    /**
     * A mesma regra, com as mensagens do fluxo que chamou.
     *
     * <p>O P0.4c reusa esta autorizacao na edicao, e as mensagens do S2a falam em "criar"
     * e "cadastrar" -- dizer ao gestor que ele nao pode criar alguem quando o que ele
     * tentou foi alterar seria informacao errada. O status e o campo "error" nao mudam:
     * a acao corretiva continua a mesma.
     */
    private void autorizarSobreUsuario(User autor, Perfil perfilAlvo, Long regionalIdAlvo,
                                       String mensagemPerfil, String mensagemRegional) {
        if (Perfil.ADMINISTRADOR.equals(autor.getPerfil())) {
            return;
        }

        if (!Perfil.GESTOR_FROTA.equals(autor.getPerfil())) {
            throw new PerfilNaoPermitidoException(mensagemPerfil);
        }

        if (!Perfil.COLABORADOR.equals(perfilAlvo)) {
            throw new PerfilNaoPermitidoException(mensagemPerfil);
        }

        // Gestor sem regional nao cadastra ninguem: nao ha "propria regional" com que
        // comparar, e o caminho permissivo seria deixa-lo criar em qualquer lugar.
        if (autor.getRegional() == null
                || !autor.getRegional().getId().equals(regionalIdAlvo)) {
            throw new RegionalNaoPermitidaException(mensagemRegional);
        }
    }

    /**
     * Leitura: ADMINISTRADOR ve qualquer um; GESTOR_FROTA ve quem esta na propria
     * regional, de qualquer perfil; COLABORADOR ve apenas a si mesmo. Todo mundo ve o
     * proprio registro.
     *
     * <p>A leitura e mais larga que a edicao de proposito: o gestor precisa conseguir
     * consultar o cadastro de quem trabalha com ele sem, por isso, poder altera-lo.
     */
    private void autorizarLeitura(User autor, User alvo) {
        if (Perfil.ADMINISTRADOR.equals(autor.getPerfil()) || ehOMesmoUsuario(autor, alvo)) {
            return;
        }

        if (!Perfil.GESTOR_FROTA.equals(autor.getPerfil())) {
            throw new PerfilNaoPermitidoException(PERFIL_NAO_PERMITIDO_LEITURA_MESSAGE);
        }

        if (autor.getRegional() == null
                || !autor.getRegional().getId().equals(regionalIdDe(alvo))) {
            throw new RegionalNaoPermitidaException(REGIONAL_NAO_PERMITIDA_LEITURA_MESSAGE);
        }
    }

    /**
     * Edicao: as mesmas regras da criacao (S2a), aplicadas ao perfil e a regional que o
     * alvo tem hoje, mais o autosservico -- qualquer um altera o proprio registro.
     *
     * <p>Reusar {@code autorizarSobreUsuario} nao e economia de linha. Um gestor que cria
     * apenas COLABORADOR mas edita qualquer um da regional teria a escrita mais permissiva
     * que a criacao, e dois gestores da mesma regional poderiam trocar a senha um do outro
     * -- escalacao lateral por um caminho que a criacao ja fecha.
     */
    private void autorizarEdicao(User autor, User alvo) {
        if (ehOMesmoUsuario(autor, alvo)) {
            return;
        }

        autorizarSobreUsuario(autor, alvo.getPerfil(), regionalIdDe(alvo),
                PERFIL_NAO_PERMITIDO_EDICAO_MESSAGE, REGIONAL_NAO_PERMITIDA_EDICAO_MESSAGE);
    }

    /**
     * Exclusao: somente ADMINISTRADOR, e nunca a propria conta.
     *
     * <p>O perfil e conferido antes do autosservico de proposito. Um colaborador tentando
     * excluir a si mesmo leva "somente um administrador pode excluir usuarios", que e
     * verdade; a mensagem de autoexclusao daria a entender que ele poderia excluir outra
     * pessoa.
     *
     * <p>Por que nem o administrador se exclui: a exclusao e logica e, desde o S3, usuario
     * inativo deixa de autenticar. Ele perderia o proprio acesso na hora, e nao ha
     * endpoint que reative ninguem -- se for o unico administrador, o sistema fica sem
     * caminho administrativo.
     */
    private void autorizarExclusao(User autor, User alvo) {
        if (!Perfil.ADMINISTRADOR.equals(autor.getPerfil())) {
            throw new PerfilNaoPermitidoException(PERFIL_NAO_PERMITIDO_EXCLUSAO_MESSAGE);
        }

        if (ehOMesmoUsuario(autor, alvo)) {
            throw new AutoExclusaoNaoPermitidaException(AUTO_EXCLUSAO_NAO_PERMITIDA_MESSAGE);
        }
    }

    private boolean ehOMesmoUsuario(User autor, User alvo) {
        return autor.getId() != null && autor.getId().equals(alvo.getId());
    }

    private Long regionalIdDe(User user) {
        return user.getRegional() == null ? null : user.getRegional().getId();
    }

    /**
     * Matrícula e regional são obrigatórias para COLABORADOR e GESTOR_FROTA, e opcionais
     * para ADMINISTRADOR — que é conta de infraestrutura e pode não pertencer a regional
     * nenhuma, como o administrador inicial criado pelo A3.
     *
     * <p>A regra é condicional ao perfil, então não cabe em anotação do Bean Validation
     * sem um validador customizado; fica aqui, junto da autorização, que também depende
     * do perfil.
     */
    private void validateCamposObrigatoriosDoPerfil(CreateUserRequest request) {
        if (Perfil.ADMINISTRADOR.equals(request.perfil())) {
            return;
        }
        if (isBlank(request.matricula())) {
            throw new InvalidUserDataException(MATRICULA_OBRIGATORIA_MESSAGE);
        }
        if (request.regionalId() == null) {
            throw new InvalidUserDataException(REGIONAL_OBRIGATORIA_MESSAGE);
        }
    }

    private Regional resolverRegional(Long regionalId) {
        if (regionalId == null) {
            return null;
        }
        return regionalRepository.findById(regionalId)
                .orElseThrow(() -> new NotFoundException(REGIONAL_NOT_FOUND_MESSAGE));
    }

    /**
     * As checagens de existência acima têm janela de corrida entre ler e escrever: duas
     * requisições simultâneas com o mesmo e-mail leriam as duas "não existe" e as duas
     * tentariam inserir. A constraint do banco é a garantia real — mesmo raciocínio do
     * {@code AdministradorInicialInitializer}. Sem este catch a violação escaparia como
     * {@code DataIntegrityViolationException}, que não tem handler registrado nem
     * fallback, virando um 500 cru.
     *
     * <p>A distinção entre matrícula e e-mail sai do nome do índice na mensagem da
     * exceção, o que é frágil por depender de texto de driver; por isso o e-mail é o
     * caso default, e não o contrário. Errar a mensagem num 409 é aceitável, devolver
     * 500 não.
     */
    private User salvarComBackstop(User user) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            String detalhe = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (detalhe.contains(INDICE_MATRICULA)) {
                throw new MatriculaDuplicadaException(MATRICULA_ALREADY_EXISTS_MESSAGE);
            }
            throw new UserAlreadyExistsException(EMAIL_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void validateMatriculaDoesNotExist(String matricula) {
        if (matricula == null) {
            return;
        }
        if (userRepository.existsByMatricula(matricula)) {
            throw new MatriculaDuplicadaException(MATRICULA_ALREADY_EXISTS_MESSAGE);
        }
    }

    /**
     * Alteracao administrativa e autosservico pelo mesmo endpoint, com a autorizacao
     * antes de qualquer escrita.
     *
     * <p>{@code UpdateUserRequest} continua sem {@code perfil} e sem {@code regionalId} --
     * promover alguem segue sendo UPDATE manual no banco. Por isso nao ha guarda contra
     * autopromocao aqui: ela nao tem campo por onde acontecer, e um teste de campo
     * inexistente seria codigo morto sugerindo o contrario.
     *
     * <p>A ordem e a mesma dos outros fluxos: buscar o alvo, autorizar, so entao validar e
     * escrever. Conferir "ja excluido" antes de autorizar diria a quem nao pode ver o
     * usuario se ele esta ativo.
     */
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        User alvo = buscarUsuarioPorId(userId);

        autorizarEdicao(autor, alvo);
        validateUserIsActive(alvo);

        updateNameIfProvided(alvo, request.name());
        updateEmailIfProvided(alvo, request.email());
        updatePasswordIfProvided(autor, alvo, request.password());

        User updatedUser = userRepository.save(alvo);
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        User alvo = buscarUsuarioPorId(userId);

        autorizarExclusao(autor, alvo);
        validateUserIsActive(alvo);

        alvo.setActive(false);
        userRepository.save(alvo);
    }

    public UserResponse getUserById(Long userId) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        User alvo = buscarUsuarioPorId(userId);

        autorizarLeitura(autor, alvo);
        validateUserIsActive(alvo);

        return userMapper.toResponse(alvo);
    }

    /**
     * Listagem escopada pelo perfil de quem chama: ADMINISTRADOR ve todos, GESTOR_FROTA
     * ve a propria regional, COLABORADOR ve apenas a si mesmo. O gestor <b>sem</b>
     * regional cai no ultimo caso -- nao ha escopo com que filtrar, e o caminho permissivo
     * seria mostrar-lhe o sistema inteiro.
     *
     * <p><b>Por que a listagem entrou junto do GET /{id}.</b> Sem ela a restricao do
     * detalhe seria contornavel em uma requisicao: o mesmo dado sairia por
     * {@code GET /api/users}, que ate aqui devolvia todo mundo para qualquer autenticado.
     *
     * <p>Nao ha 403 neste fluxo, e isso e deliberado: a resposta e uma pagina, e uma
     * pagina reduzida (ou vazia) e a resposta correta para "estes sao os usuarios que voce
     * pode ver". Recusar a rota inteira obrigaria o frontend a tratar erro onde nao ha
     * erro.
     */
    public Page<UserResponse> getAllUsers(Boolean active, String name, Pageable pageable) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        return buscarUsuariosVisiveis(autor, active, name, pageable).map(userMapper::toResponse);
    }

    private Page<User> buscarUsuariosVisiveis(User autor, Boolean active, String name, Pageable pageable) {
        if (Perfil.ADMINISTRADOR.equals(autor.getPerfil())) {
            return userRepository.findByIsActiveAndNameContainingIgnoreCase(active, name, pageable);
        }

        if (Perfil.GESTOR_FROTA.equals(autor.getPerfil()) && autor.getRegional() != null) {
            return userRepository.findByIsActiveAndRegionalIdAndNameContainingIgnoreCase(
                    active, autor.getRegional().getId(), name, pageable);
        }

        return userRepository.findByIsActiveAndIdAndNameContainingIgnoreCase(
                active, autor.getId(), name, pageable);
    }

    public Long countAllActiveUsers() {
        return userRepository.countByIsActiveTrue();
    }

    private User findUserByIdOrThrow(Long userId) {
        User user = buscarUsuarioPorId(userId);
        validateUserIsActive(user);
        return user;
    }

    /**
     * Busca sem conferir {@code is_active}, para os fluxos que precisam autorizar antes de
     * validar: o alvo tem de estar em maos para se decidir se quem chamou pode ve-lo, e so
     * depois disso "ja excluido" pode ser dito em voz alta.
     */
    private User buscarUsuarioPorId(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
    }

    private void validateUserIsActive(User user) {
        if (!user.getActive()) {
            throw new UserAlreadyDeletedException(USER_ALREADY_DELETED_MESSAGE);
        }
    }

    private void validateEmailDoesNotExist(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(EMAIL_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void updateNameIfProvided(User user, String name) {
        if (isNotBlank(name)) {
            user.setName(name);
        }
    }

    /**
     * O e-mail atual do proprio usuario nao conta como duplicado. Sem esta comparacao um
     * PATCH que reenvia o formulario inteiro sem mexer no e-mail levaria 409, porque
     * {@code existsByEmail} encontra o registro do proprio alvo -- caminho que passou a
     * ser comum quando o autosservico virou o uso principal do endpoint.
     */
    private void updateEmailIfProvided(User user, String email) {
        if (isBlank(email) || email.equals(user.getEmail())) {
            return;
        }
        validateEmailDoesNotExist(email);
        user.setEmail(email);
    }

    /**
     * Senha so pelo proprio dono, e sujeita a mesma politica do S3.
     *
     * <p>Ate o P0.4c este era o unico ponto do sistema em que alguem escolhia a senha de
     * outra pessoa, e sem autorizacao nenhuma: qualquer autenticado trocava a senha de um
     * administrador e assumia a conta. Escopar por perfil nao bastaria -- quem pudesse
     * editar continuaria podendo assumir a conta editada.
     *
     * <p>A validacao tambem faltava aqui, o que fazia do PATCH um segundo caminho para
     * senha fraca depois de o S3 ter fechado o da ativacao. Ela roda sobre o e-mail e o
     * nome <b>ja atualizados nesta mesma requisicao</b>, porque as tres alteracoes
     * acontecem em ordem: uma senha que contenha o nome novo deve reprovar.
     */
    private void updatePasswordIfProvided(User autor, User alvo, String password) {
        if (isBlank(password)) {
            return;
        }

        if (!ehOMesmoUsuario(autor, alvo)) {
            throw new SenhaDeTerceiroException(SENHA_DE_TERCEIRO_MESSAGE);
        }

        UserValidator.validarSenha(password, alvo.getEmail(), alvo.getName());

        alvo.setPassword(passwordEncoder.encode(password));
        // Mantem o invariante password != null <=> senhaDefinida. Sem isto, um
        // usuario criado sem senha continuaria sem conseguir logar depois de
        // receber uma.
        alvo.setSenhaDefinida(true);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}