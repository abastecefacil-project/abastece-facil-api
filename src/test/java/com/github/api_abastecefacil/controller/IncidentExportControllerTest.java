package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.service.IncidentService;
import com.github.api_abastecefacil.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class IncidentExportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean IncidentService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exportacaoExigeLogin() throws Exception {
        mvc.perform(get("/api/incidents/export")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void devolveXlsxComoAnexoComDataNoNome() throws Exception {
        LocalDate data = LocalDate.of(2026, 8, 18);
        when(service.exportarOcorrencias("ABC1234", "sujo", "fulano", data)).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/incidents/export").param("carPlate", "ABC1234").param("title", "sujo")
                        .param("userName", "fulano").param("occurrenceDate", "2026-08-18")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"ocorrencias-\\d{4}-\\d{2}-\\d{2}\\.xlsx\"")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void perfilRecusadoPeloServicoRecebe403() throws Exception {
        when(service.exportarOcorrencias(null, null, null, null))
                .thenThrow(new PerfilNaoPermitidoException("Sem permissão"));
        mvc.perform(get("/api/incidents/export").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PERFIL_NAO_PERMITIDO"));
    }
}
