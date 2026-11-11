package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.service.CarService;
import com.github.api_abastecefacil.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CarController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CarExportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CarService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exportacaoExigeLogin() throws Exception {
        mvc.perform(get("/api/cars/export")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void devolveXlsxComoAnexoComDataNoNome() throws Exception {
        when(service.exportarVeiculos("civic", true)).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/cars/export").param("search", "civic").param("active", "true")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"veiculos-\\d{4}-\\d{2}-\\d{2}\\.xlsx\"")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void perfilRecusadoPeloServicoRecebe403() throws Exception {
        when(service.exportarVeiculos(null, null)).thenThrow(new PerfilNaoPermitidoException("Sem permissão"));
        mvc.perform(get("/api/cars/export").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PERFIL_NAO_PERMITIDO"));
    }

    @Test
    void corsExpoeContentDisposition() throws Exception {
        when(service.exportarVeiculos(null, null)).thenReturn(new byte[0]);
        mvc.perform(get("/api/cars/export").header("Origin", "https://front.exemplo.com")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")));
    }
}
