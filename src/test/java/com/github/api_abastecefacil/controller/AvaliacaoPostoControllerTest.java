package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.service.AvaliacaoPostoService;
import com.github.api_abastecefacil.service.JwtService;
import com.github.api_abastecefacil.dto.gasStation.AvaliacaoPostoRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
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

@WebMvcTest(AvaliacaoPostoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AvaliacaoPostoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AvaliacaoPostoService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void exigeLoginEmTodasAsRotas() throws Exception {
        mvc.perform(get("/api/gas-stations/avaliacoes/resumos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/gas-stations/1/avaliacoes")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/gas-stations/1/avaliacoes")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/gas-stations/1/avaliacoes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":5}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejeitaNotasAusentesOuForaDaEscalaEComentariosLongos() throws Exception {
        for (var corpo : new String[]{"{}", "{\"nota\":null}", "{\"nota\":0}", "{\"nota\":6}", "{\"nota\":3.5}",
                "{\"nota\":5,\"comentario\":\"" + "x".repeat(1001) + "\"}"}) {
            mvc.perform(put("/api/gas-stations/1/avaliacoes").with(user("colaborador"))
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void aceitaLimitesDaEscalaEComentarioOpcional() throws Exception {
        for (int nota : new int[]{1, 5}) {
            mvc.perform(put("/api/gas-stations/1/avaliacoes").with(user("colaborador"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":" + nota + "}"))
                    .andExpect(status().isOk());
            verify(service).salvar(1L, new AvaliacaoPostoRequest(BigDecimal.valueOf(nota), null));
        }
    }
}
