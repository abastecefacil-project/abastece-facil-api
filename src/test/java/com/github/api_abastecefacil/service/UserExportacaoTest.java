package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static com.github.api_abastecefacil.util.PlanilhaTeste.abrir;
import static com.github.api_abastecefacil.util.PlanilhaTeste.linha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserExportacaoTest {
    private static final Pageable SEM_PAGINACAO_POR_NOME = Pageable.unpaged(Sort.by("name"));

    private final UserRepository users = mock(UserRepository.class);
    private final UsuarioAutenticadoProvider autenticado = mock(UsuarioAutenticadoProvider.class);
    private final UserService service = new UserService(users, null, null, null, autenticado, null,
            new AutorizacaoOperacional(autenticado), List.of());
    private final Regional joinville = new Regional().setId(1L).setNome("Joinville");

    @Test
    void cabecalhosSaoExatamenteEstesSemNenhumCampoDeCredencial() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(users.findByIsActiveAndNameContainingIgnoreCase(true, "", SEM_PAGINACAO_POR_NOME))
                .thenReturn(new PageImpl<>(List.of(usuario())));

        try (Workbook planilha = abrir(service.exportarUsuarios(true, ""))) {
            Sheet aba = planilha.getSheetAt(0);
            assertThat(linha(aba, 0)).containsExactly(
                    "Nome", "E-mail", "Telefone", "Matrícula", "Perfil", "Regional", "Situação",
                    "Cadastrado em", "Atualizado em");
            assertThat(linha(aba, 1)).containsExactly(
                    "Mariana Prado", "mariana@fiesc.org.br", "47999998888", "012345", "Gestor de frota",
                    "Joinville", "Ativo", "05/03/2026", "");
            assertThat(linha(aba, 1)).doesNotContain("$2a$10$hashquenaopodesair");
        }
    }

    @Test
    void telefoneEMatriculaSaemComoTexto() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(users.findByIsActiveAndNameContainingIgnoreCase(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(usuario())));

        try (Workbook planilha = abrir(service.exportarUsuarios(true, ""))) {
            Sheet aba = planilha.getSheetAt(0);
            assertThat(aba.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(aba.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.STRING);
        }
    }

    @Test
    void perfilSaiEmPortugues() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(users.findByIsActiveAndNameContainingIgnoreCase(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(
                        new User().setName("A").setPerfil(Perfil.ADMINISTRADOR).setActive(true),
                        new User().setName("B").setPerfil(Perfil.COLABORADOR).setActive(true))));

        try (Workbook planilha = abrir(service.exportarUsuarios(true, ""))) {
            Sheet aba = planilha.getSheetAt(0);
            assertThat(linha(aba, 1).get(4)).isEqualTo("Administrador");
            assertThat(linha(aba, 2).get(4)).isEqualTo("Colaborador");
        }
    }

    @Test
    void gestorExportaSoASuaRegional() {
        autenticar(new User().setId(9L).setPerfil(Perfil.GESTOR_FROTA).setRegional(joinville));
        when(users.findByIsActiveAndRegionalIdAndNameContainingIgnoreCase(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.exportarUsuarios(false, "ana");

        verify(users).findByIsActiveAndRegionalIdAndNameContainingIgnoreCase(false, 1L, "ana", SEM_PAGINACAO_POR_NOME);
        verifyNoMoreInteractions(users);
    }

    @Test
    void gestorSemRegionalExportaSoASiMesmo() {
        // Mesmo escopo da listagem (EscopoUsuarios.Proprio), e não 403 como em veículos.
        autenticar(new User().setId(9L).setPerfil(Perfil.GESTOR_FROTA));
        when(users.findByIsActiveAndIdAndNameContainingIgnoreCase(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.exportarUsuarios(true, "");

        verify(users).findByIsActiveAndIdAndNameContainingIgnoreCase(true, 9L, "", SEM_PAGINACAO_POR_NOME);
        verifyNoMoreInteractions(users);
    }

    @Test
    void colaboradorNaoExporta() {
        autenticar(new User().setId(5L).setPerfil(Perfil.COLABORADOR));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.exportarUsuarios(true, ""));
        verifyNoInteractions(users);
    }

    private void autenticar(User usuario) {
        when(autenticado.obterUsuarioAutenticado()).thenReturn(usuario);
    }

    private User usuario() {
        return new User().setName("Mariana Prado").setEmail("mariana@fiesc.org.br")
                .setPassword("$2a$10$hashquenaopodesair").setTelefone("47999998888").setMatricula("012345")
                .setPerfil(Perfil.GESTOR_FROTA).setRegional(joinville).setActive(true).setSenhaDefinida(true)
                .setCreatedAt(LocalDateTime.of(2026, 3, 5, 14, 30));
    }
}
