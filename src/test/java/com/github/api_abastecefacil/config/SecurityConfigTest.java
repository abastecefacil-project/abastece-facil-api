package com.github.api_abastecefacil.config;

import com.github.api_abastecefacil.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTest.Endpoints.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityConfigTest.Endpoints.class})
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exigeLoginParaPostosEOcorrencias() throws Exception {
        mvc.perform(get("/api/public/gas-stations/filter")).andExpect(status().isForbidden());
        mvc.perform(post("/api/public/incident")).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/gas-stations/filter").with(user("colaborador")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/public/incident").with(user("colaborador")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
    }

    @RestController
    static class Endpoints {
        @GetMapping("/api/public/gas-stations/filter")
        String postos() { return "ok"; }

        @PostMapping({"/api/public/incident", "/api/auth/login"})
        String post() { return "ok"; }
    }
}
