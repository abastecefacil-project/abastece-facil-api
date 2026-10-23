package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.service.JwtService;
import com.github.api_abastecefacil.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserStatusControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void reativacaoExigeLogin() throws Exception {
        mvc.perform(post("/api/users/7/reativar")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void reativacaoDelegaAutorizacaoAoServico() throws Exception {
        mvc.perform(post("/api/users/7/reativar").with(user("admin"))).andExpect(status().isOk());
        verify(service).reativarUser(7L);
    }
}
