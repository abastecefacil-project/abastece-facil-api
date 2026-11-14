package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.dto.notificacao.NotificacoesResponse;
import com.github.api_abastecefacil.service.JwtService;
import com.github.api_abastecefacil.service.NotificacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificacaoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class NotificacaoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificacaoService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/api/notificacoes")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notificacoes/1/lida")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rotasMapeadas() throws Exception {
        when(service.listar(any(Pageable.class))).thenReturn(new NotificacoesResponse(Page.empty(), 4));
        mvc.perform(get("/api/notificacoes").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(4));
        mvc.perform(patch("/api/notificacoes/1/lida").with(user("admin"))).andExpect(status().isNoContent());
        verify(service).marcarLida(1L);
    }
}
