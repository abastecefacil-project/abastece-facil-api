package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.service.GasStationService;
import com.github.api_abastecefacil.service.JwtService;
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

@WebMvcTest(GasStationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class GasStationExportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean GasStationService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exportacaoExigeLogin() throws Exception {
        mvc.perform(get("/api/gas-stations/export")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void devolveXlsxComoAnexoComDataNoNome() throws Exception {
        when(service.exportarPostos("zandona", true)).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/gas-stations/export").param("search", "zandona").param("active", "true")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"postos-\\d{4}-\\d{2}-\\d{2}\\.xlsx\"")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void perfilRecusadoPeloServicoRecebe403() throws Exception {
        when(service.exportarPostos(null, null)).thenThrow(new PerfilNaoPermitidoException("Sem permissão"));
        mvc.perform(get("/api/gas-stations/export").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PERFIL_NAO_PERMITIDO"));
    }
}
