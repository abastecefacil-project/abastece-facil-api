package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioAutenticadoProviderTest {

    private static final String EMAIL = "gestor@fiesc.org.br";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UsuarioAutenticadoProvider provider;

    @AfterEach
    void tearDown() {
        // O contexto e um ThreadLocal: sem limpar, o teste seguinte herdaria a
        // autenticacao deste e passaria por motivo errado.
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new org.springframework.security.core.userdetails.User(
                                email, "", List.of(new SimpleGrantedAuthority("ROLE_GESTOR_FROTA"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_GESTOR_FROTA"))));
    }

    @Test
    void obterUsuarioAutenticado_ShouldReturnTheEntity_WithRegionalAttached() {
        // O ponto do componente: o UserDetails do contexto so tem e-mail e authority, e a
        // regional -- que a autorizacao do S2a precisa -- so existe na entidade.
        Regional joinville = new Regional().setId(1L).setSigla("JOI");
        User gestor = new User().setId(9L).setEmail(EMAIL).setPerfil(Perfil.GESTOR_FROTA).setRegional(joinville);

        autenticar(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gestor));

        User resultado = provider.obterUsuarioAutenticado();

        assertThat(resultado).isSameAs(gestor);
        assertThat(resultado.getRegional()).isSameAs(joinville);
        assertThat(resultado.getPerfil()).isEqualTo(Perfil.GESTOR_FROTA);
    }

    @Test
    void obterUsuarioAutenticado_ShouldLookUpByTheAuthenticatedEmail() {
        User outro = new User().setEmail("outro@fiesc.org.br");
        autenticar("outro@fiesc.org.br");
        when(userRepository.findByEmail("outro@fiesc.org.br")).thenReturn(Optional.of(outro));

        assertThat(provider.obterUsuarioAutenticado()).isSameAs(outro);
    }

    @Test
    void obterUsuarioAutenticado_ShouldThrow_WhenThereIsNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> provider.obterUsuarioAutenticado())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void obterUsuarioAutenticado_ShouldThrow_WhenTheAuthenticatedUserNoLongerExists() {
        // Token valido de usuario removido fisicamente do banco. Nao e entrada invalida do
        // usuario, e estado inconsistente -- por isso IllegalStateException, e nao excecao
        // de negocio.
        autenticar(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.obterUsuarioAutenticado())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EMAIL);
    }
}
