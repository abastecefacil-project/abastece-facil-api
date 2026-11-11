package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.service.JwtService;
import com.github.api_abastecefacil.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserExportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exportacaoExigeLogin() throws Exception {
        mvc.perform(get("/api/users/export").param("active", "true")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void devolveXlsxComoAnexoComDataNoNome() throws Exception {
        when(service.exportarUsuarios(true, "ana")).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/users/export").param("active", "true").param("name", "ana")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"usuarios-\\d{4}-\\d{2}-\\d{2}\\.xlsx\"")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void activeEObrigatorioENameTemDefaultVazio() throws Exception {
        mvc.perform(get("/api/users/export").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);

        when(service.exportarUsuarios(false, "")).thenReturn(new byte[0]);
        mvc.perform(get("/api/users/export").param("active", "false").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        verify(service).exportarUsuarios(false, "");
    }

    @Test
    void perfilRecusadoPeloServicoRecebe403() throws Exception {
        when(service.exportarUsuarios(true, "")).thenThrow(new PerfilNaoPermitidoException("Sem permissão"));
        mvc.perform(get("/api/users/export").param("active", "true").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PERFIL_NAO_PERMITIDO"));
    }
}
