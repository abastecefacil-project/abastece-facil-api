package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.service.AvaliacaoNeoService;
import com.github.api_abastecefacil.service.JwtService;
import com.github.api_abastecefacil.dto.gasStation.AvaliacaoNeoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(AvaliacaoNeoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AvaliacaoNeoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AvaliacaoNeoService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exigeLoginParaConsultarEResponder() throws Exception {
        mvc.perform(get("/api/gas-stations/1/neo")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/gas-stations/1/neo")).andExpect(status().isForbidden());
        mvc.perform(put("/api/gas-stations/1/neo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"passouNeo\":true}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void removePropriaRespostaERetornaTotais() throws Exception {
        when(service.remover(1L)).thenReturn(new AvaliacaoNeoResponse(null, 2, 1, null));
        mvc.perform(delete("/api/gas-stations/1/neo").with(user("colaborador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minhaResposta").isEmpty())
                .andExpect(jsonPath("$.passaram").value(2));
        verify(service).remover(1L);
    }

    @Test
    void respostaEObrigatoriaMasFalseEValido() throws Exception {
        for (var corpo : new String[]{"{}", "{\"passouNeo\":null}"}) {
            mvc.perform(put("/api/gas-stations/1/neo").with(user("colaborador"))
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.salvar(1L, false)).thenReturn(new AvaliacaoNeoResponse(false, 3, 2, null));
        mvc.perform(put("/api/gas-stations/1/neo").with(user("colaborador"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"passouNeo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minhaResposta").value(false))
                .andExpect(jsonPath("$.passaram").value(3))
                .andExpect(jsonPath("$.naoPassaram").value(2));
        verify(service).salvar(1L, false);
    }
}
