package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.exception.DesativacaoNaoConfirmadaException;
import com.github.api_abastecefacil.exception.PlanoImportacaoDivergenteException;
import com.github.api_abastecefacil.service.ImportacaoPostosService;
import com.github.api_abastecefacil.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP do início da importação com a ciência das desativações. A regra de quando a
 * ciência é exigida, e a prova de que nada é reservado na recusa, estão no
 * ImportacaoPostosServiceTest; aqui se prova que o parâmetro chega ao serviço e que as duas
 * recusas saem com o status e o "error" próprios.
 */
@WebMvcTest(ImportacaoPostosController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ImportacaoPostosControllerTest {

    private static final MockMultipartFile ARQUIVO = new MockMultipartFile(
            "arquivo", "postos.xlsx", "application/octet-stream", new byte[]{1, 2, 3});

    @Autowired MockMvc mvc;
    @MockitoBean ImportacaoPostosService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void iniciaSemParametro_QuandoOPlanoNaoDesativa() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.iniciar(any(), isNull())).thenReturn(id);

        mvc.perform(multipart("/api/gas-stations/import").file(ARQUIVO)
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/gas-stations/import/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(service).iniciar(any(), isNull());
    }

    @Test
    void recusaCom422_QuandoOPlanoDesativaEOParametroEstaAusente() throws Exception {
        when(service.iniciar(any(), isNull())).thenThrow(new DesativacaoNaoConfirmadaException(
                "Confirme a desativação de 3 postos antes de iniciar a importação."));

        mvc.perform(multipart("/api/gas-stations/import").file(ARQUIVO)
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("DESATIVACAO_NAO_CONFIRMADA"))
                .andExpect(jsonPath("$.message")
                        .value("Confirme a desativação de 3 postos antes de iniciar a importação."));
    }

    @Test
    void iniciaERepassaAQuantidade_QuandoElaConfereComOPlano() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.iniciar(any(), eq(3))).thenReturn(id);

        mvc.perform(multipart("/api/gas-stations/import").file(ARQUIVO)
                        .param("desativacoesConfirmadas", "3")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(service).iniciar(any(), eq(3));
    }

    @Test
    void recusaCom409_QuandoAQuantidadeDivergeDoPlanoRecalculado() throws Exception {
        when(service.iniciar(any(), eq(3))).thenThrow(new PlanoImportacaoDivergenteException(
                "A quantidade de postos a desativar mudou desde a prévia (confirmados: 3, atuais: 5)."));

        mvc.perform(multipart("/api/gas-stations/import").file(ARQUIVO)
                        .param("desativacoesConfirmadas", "3")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PLANO_IMPORTACAO_DIVERGENTE"));
    }
}
