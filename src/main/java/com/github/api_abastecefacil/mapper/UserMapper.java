package com.github.api_abastecefacil.mapper;


import com.github.api_abastecefacil.dto.user.CreateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final RegionalMapper regionalMapper;

    public UserMapper(RegionalMapper regionalMapper) {
        this.regionalMapper = regionalMapper;
    }

    /**
     * Cadastro administrativo ({@code POST /api/users}) — desde o S8, o <b>único</b>
     * caminho de criação de usuário do sistema.
     *
     * <p>Três propriedades centrais ao S2a: o <b>perfil vem do request</b>; a
     * <b>regional é gravada</b> (a entidade já resolvida chega pronta, para o mapper não
     * tocar em repositório); e a <b>senha é nula com {@code senhaDefinida = false}</b>.
     *
     * <p>Não há senha a codificar, e desde o S8 <b>o mapper nem recebe mais o
     * {@code PasswordEncoder}</b>: ele existia aqui só para o registro público, removido
     * junto com o endpoint. O usuário nasce sem senha e sem meio de entrar, até o convite
     * do S2b1; o {@code AuthService.validateSenhaDefinida} é quem barra o login nesse
     * intervalo, com 401 {@code PASSWORD_NOT_SET}.
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
