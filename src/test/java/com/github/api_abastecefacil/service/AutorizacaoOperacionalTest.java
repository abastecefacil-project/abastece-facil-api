package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * A regra do P0.4 vive aqui, e é aqui que ela é provada. Os testes dos três serviços
 * verificam apenas que a guarda é <b>chamada</b>, e antes de tudo.
 */
@ExtendWith(MockitoExtension.class)
class AutorizacaoOperacionalTest {

    @Mock
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    private AutorizacaoOperacional autorizacao;

    @BeforeEach
    void setUp() {
        autorizacao = new AutorizacaoOperacional(usuarioAutenticadoProvider);
    }

    private void autenticadoComo(Perfil perfil) {
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(new User().setId(1L).setEmail("autor@fiesc.org.br").setPerfil(perfil));
    }

    // ------------------------------------------------------------------- escrita

    @Test
    void autorizarEscrita_ShouldAllowAdministrador() {
        autenticadoComo(Perfil.ADMINISTRADOR);
        assertThatCode(() -> autorizacao.autorizarEscrita()).doesNotThrowAnyException();
    }

    @Test
    void autorizarEscrita_ShouldAllowGestorFrota() {
        autenticadoComo(Perfil.GESTOR_FROTA);
        assertThatCode(() -> autorizacao.autorizarEscrita()).doesNotThrowAnyException();
    }

    @Test
    void autorizarEscrita_ShouldRejectColaborador() {
        autenticadoComo(Perfil.COLABORADOR);
        assertThrows(PerfilNaoPermitidoException.class, () -> autorizacao.autorizarEscrita());
    }

    // ------------------------------------------------------------------ consulta

    @Test
    void autorizarConsulta_ShouldAllowAdministrador() {
        autenticadoComo(Perfil.ADMINISTRADOR);
        assertThatCode(() -> autorizacao.autorizarConsulta()).doesNotThrowAnyException();
    }

    @Test
    void autorizarConsulta_ShouldAllowGestorFrota() {
        autenticadoComo(Perfil.GESTOR_FROTA);
        assertThatCode(() -> autorizacao.autorizarConsulta()).doesNotThrowAnyException();
    }

    @Test
    void autorizarConsulta_ShouldRejectColaborador() {
        autenticadoComo(Perfil.COLABORADOR);
        assertThrows(PerfilNaoPermitidoException.class, () -> autorizacao.autorizarConsulta());
    }

    // ------------------------------------------------------------------ detalhes

    @Test
    void autorizarEscritaEConsulta_ShouldUseDifferentMessages() {
        // Mesma regra, mensagens distintas: quem le precisa saber o que foi recusado.
        // O campo "error" do ErrorResponse e o mesmo nos dois, porque a acao corretiva e
        // a mesma -- falar com quem tem o perfil.
        autenticadoComo(Perfil.COLABORADOR);

        String escrita = assertThrows(PerfilNaoPermitidoException.class,
                () -> autorizacao.autorizarEscrita()).getMessage();
        String consulta = assertThrows(PerfilNaoPermitidoException.class,
                () -> autorizacao.autorizarConsulta()).getMessage();

        assertThat(escrita).isNotEqualTo(consulta);
        assertThat(escrita).contains("alterar");
        assertThat(consulta).contains("consultar");
    }

    @Test
    void autorizar_ShouldNotDependOnRegional() {
        // Posto, veiculo e ocorrencia nao tem regional, entao nao ha o que segmentar:
        // gestor sem regional escreve igual ao gestor com regional. E o oposto de
        // UserService.autorizarSobreUsuario, onde gestor sem regional nao cadastra
        // ninguem -- ali existe alvo com regional para comparar, aqui nao.
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado())
                .thenReturn(new User().setId(2L).setPerfil(Perfil.GESTOR_FROTA).setRegional(null));

        assertThatCode(() -> autorizacao.autorizarEscrita()).doesNotThrowAnyException();
    }

    @Test
    void autorizar_ShouldReadPerfilFromDatabaseEntity_NotFromToken() {
        // Nao ha assercao possivel sobre o JWT aqui: o ponto e que a unica fonte de
        // perfil e o UsuarioAutenticadoProvider, que le do banco. Se alguem trocar por
        // leitura de claim, este teste deixa de compilar ou de fazer sentido.
        User autor = new User().setId(3L).setPerfil(Perfil.COLABORADOR)
                .setRegional(new Regional().setId(1L).setSigla("JOI"));
        when(usuarioAutenticadoProvider.obterUsuarioAutenticado()).thenReturn(autor);

        assertThrows(PerfilNaoPermitidoException.class, () -> autorizacao.autorizarEscrita());
    }
}
