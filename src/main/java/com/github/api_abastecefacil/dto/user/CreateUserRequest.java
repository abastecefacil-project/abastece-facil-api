package com.github.api_abastecefacil.dto.user;

import com.github.api_abastecefacil.model.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload do cadastro administrativo, {@code POST /api/users}.
 *
 * <p>Nasceu separado do {@code RegisterRequest} do registro público. Até o S2a os dois
 * fluxos compartilhavam o mesmo record, o que fazia o endpoint administrativo exigir
 * senha e ser incapaz de gravar perfil, regional, telefone e matrícula; alterar o record
 * compartilhado quebraria {@code POST /api/auth/register}. O S8 removeu aquele endpoint e
 * o seu DTO, e este passou a ser o <b>único</b> payload de criação de usuário — mas a
 * separação foi o que permitiu chegar até aqui.
 *
 * <p><b>Não tem {@code password}, e isso é a regra central deste fluxo.</b> O usuário
 * nasce com senha nula e {@code senhaDefinida = false}; quem define a senha é o próprio
 * convidado, pelo link de ativação que o S2b1 vai disparar. Um administrador nunca
 * escolhe a senha de outra pessoa.
 *
 * <p>As anotações cobrem só o que não depende do perfil. {@code matricula} e
 * {@code regionalId} são obrigatórias para COLABORADOR e GESTOR_FROTA e opcionais para
 * ADMINISTRADOR — regra condicional que o Bean Validation não expressa sem um validador
 * customizado, e que por isso vive no {@code UserService}, junto com a autorização.
 */
public record CreateUserRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        String email,

        String telefone,

        String matricula,

        @NotNull(message = "Perfil é obrigatório")
        Perfil perfil,

        Long regionalId
) {
}
