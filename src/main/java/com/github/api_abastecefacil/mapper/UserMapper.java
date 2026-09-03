package com.github.api_abastecefacil.mapper;


import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.user.CreateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final PasswordEncoder passwordEncoder;
    private final RegionalMapper regionalMapper;

    public UserMapper(PasswordEncoder passwordEncoder, RegionalMapper regionalMapper) {
        this.passwordEncoder = passwordEncoder;
        this.regionalMapper = regionalMapper;
    }

    /**
     * Registro público ({@code POST /api/auth/register}). Perfil fixo em COLABORADOR e
     * senha vinda do payload — quem se registra escolhe a própria senha.
     *
     * <p>Intocado pelo S2a de propósito: este endpoint continua público e sai só no S2b.
     * Se ele mudasse junto, o cadastro administrativo e o registro público voltariam a
     * ficar acoplados, que é exatamente o problema que o S2a desfez.
     */
    public User toEntity(RegisterRequest request) {
        return new User()
                .setName(request.name())
                .setEmail(request.email())
                .setPassword(passwordEncoder.encode(request.password()))
                .setPerfil(Perfil.COLABORADOR)
                .setSenhaDefinida(true);
    }

    /**
     * Cadastro administrativo ({@code POST /api/users}).
     *
     * <p>Três diferenças em relação ao registro público, todas centrais ao S2a:
     * o <b>perfil vem do request</b> em vez de ser fixo; a <b>regional é gravada</b>
     * (a entidade já resolvida chega pronta, para o mapper não tocar em repositório); e
     * a <b>senha é nula com {@code senhaDefinida = false}</b>.
     *
     * <p>O {@code PasswordEncoder} não é chamado aqui, e não há o que codificar: o
     * usuário nasce sem senha e sem meio de entrar, até o convite do S2b1. O
     * {@code AuthService.validateSenhaDefinida} é quem barra o login nesse intervalo,
     * com 401 {@code PASSWORD_NOT_SET}.
     *
     * <p>{@code active = true} é redundante com o {@code @PrePersist} da entidade, e está
     * explícito por ser regra do fluxo, não detalhe de persistência: o usuário é criado
     * ativo, apenas sem senha.
     */
    public User toEntity(CreateUserRequest request, Regional regional) {
        return new User()
                .setName(request.name())
                .setEmail(request.email())
                .setPassword(null)
                .setSenhaDefinida(false)
                .setActive(true)
                .setPerfil(request.perfil())
                .setRegional(regional)
                // Sem normalizar aqui: o @PrePersist da entidade aplica
                // UserValidator.normalizarTelefone e grava so os digitos.
                .setTelefone(request.telefone())
                .setMatricula(request.matricula());
    }

    /**
     * Leitura: {@code conviteEnviado} vem {@code null}, porque a pergunta não se aplica —
     * a resposta descreve o usuário, não uma tentativa de envio. Ver o javadoc do campo
     * em {@link UserResponse}.
     */
    public UserResponse toResponse(User user) {
        return toResponse(user, null);
    }

    /**
     * Criação e reenvio de convite: carrega o resultado do envio junto do usuário, para o
     * gestor saber na hora se precisa reenviar.
     */
    public UserResponse toResponse(User user, Boolean conviteEnviado) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getPerfil(),
                regionalMapper.toSummaryResponse(user.getRegional()),
                user.getTelefone(),
                user.getMatricula(),
                user.getSenhaDefinida(),
                conviteEnviado
        );
    }


}
