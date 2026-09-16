package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static com.github.api_abastecefacil.constants.AuthConstants.USER_NOT_FOUND_BY_EMAIL_MESSAGE;

/**
 * Resolve o usuário autenticado da requisição corrente para a entidade de domínio.
 *
 * <p>É o primeiro ponto do projeto a <b>ler</b> o {@code SecurityContextHolder}. Até o
 * S2a ele era escrito pelo {@code JwtAuthenticationFilter} e nunca consultado: todos os
 * serviços recebiam os ids por parâmetro do controller, e nenhuma regra dependia de quem
 * estava chamando. A autorização por regional do S2a é a primeira que depende.
 *
 * <p><b>Por que uma segunda ida ao banco.</b> O filtro já fez {@code findByEmail} para
 * montar o {@code UserDetails}, mas descartou a entidade — o {@code UserDetails} do
 * Spring guarda apenas e-mail, hash de senha e authority. Id, regional, matrícula e nome
 * se perdem ali. Enquanto o {@code UserDetails} for o padrão do Spring, recuperar a
 * entidade custa uma consulta.
 *
 * <p><b>Por que não ler a regional do JWT.</b> O token não a carrega, e não deveria: ele
 * vive 24 horas, então alguém movido de regional continuaria autorizado na regional
 * antiga até o próximo login. Autorização sobre dado potencialmente desatualizado é
 * falha. Ver §6 do CLAUDE.md.
 */
@Component
public class UsuarioAutenticadoProvider {

    private final UserRepository userRepository;

    public UsuarioAutenticadoProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Devolve a entidade do usuário autenticado.
     *
     * <p>Lança {@link IllegalStateException} se não houver autenticação. Não é caso de
     * negócio: as rotas que chegam aqui estão todas atrás de
     * {@code anyRequest().authenticated()}, então contexto vazio significa erro de
     * programação — um serviço autenticado chamado de um caminho público —, e não
     * entrada inválida do usuário.
     */
    public User obterUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "Nenhum usuário autenticado no contexto de segurança. Este fluxo exige autenticação.");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(USER_NOT_FOUND_BY_EMAIL_MESSAGE + email));
    }
}
