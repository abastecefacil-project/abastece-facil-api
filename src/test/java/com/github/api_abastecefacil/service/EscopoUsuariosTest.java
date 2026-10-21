package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra de quem vê quem é provada aqui. O UserServiceTest (listagem) e o
 * DashboardServiceTest (contagem) verificam que cada escopo leva à consulta certa.
 */
class EscopoUsuariosTest {

    @Test
    void de_ShouldReturnTodosForAdministrador() {
        User admin = new User().setId(1L).setPerfil(Perfil.ADMINISTRADOR);

        assertThat(EscopoUsuarios.de(admin)).isEqualTo(new EscopoUsuarios.Todos());
    }

    @Test
    void de_ShouldReturnDaRegionalForGestorWithRegional() {
        User gestor = new User().setId(2L).setPerfil(Perfil.GESTOR_FROTA)
                .setRegional(new Regional().setId(1L));

        assertThat(EscopoUsuarios.de(gestor)).isEqualTo(new EscopoUsuarios.DaRegional(1L));
    }

    @Test
    void de_ShouldReturnProprioForGestorWithoutRegional() {
        User gestor = new User().setId(3L).setPerfil(Perfil.GESTOR_FROTA);

        assertThat(EscopoUsuarios.de(gestor)).isEqualTo(new EscopoUsuarios.Proprio(3L));
    }

    @Test
    void de_ShouldReturnProprioForColaboradorEvenWithRegional() {
        User colaborador = new User().setId(4L).setPerfil(Perfil.COLABORADOR)
                .setRegional(new Regional().setId(1L));

        assertThat(EscopoUsuarios.de(colaborador)).isEqualTo(new EscopoUsuarios.Proprio(4L));
    }
}
