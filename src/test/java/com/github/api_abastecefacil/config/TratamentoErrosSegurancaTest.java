package com.github.api_abastecefacil.config;

import com.github.api_abastecefacil.exception.GlobalExceptionHandler;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadeia de segurança e handler global juntos: o que cada falha responde em rota autenticada.
 *
 * <p>O MockMvc não faz o despacho real para {@code /error} -- não há contêiner de servlet --,
 * então a liberação do {@code DispatcherType.ERROR} é provada simulando esse despacho. A prova
 * de ponta a ponta é a chamada à API real.
 */
@WebMvcTest(TratamentoErrosSegurancaTest.Endpoints.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        TratamentoErrosSegurancaTest.Endpoints.class})
class TratamentoErrosSegurancaTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void excecaoInesperadaEmRotaAutenticadaResponde500ComCorpoPadrao() throws Exception {
        mvc.perform(get("/api/teste/inesperado").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.message").value("Ocorreu um erro inesperado. Tente novamente em instantes."))
                .andExpect(jsonPath("$.path").value("uri=/api/teste/inesperado"))
                .andExpect(content().string(not(containsString("detalhe interno"))));
    }

    @Test
    void semTokenResponde401NaoAutenticado() throws Exception {
        esperaNaoAutenticado(mvc.perform(get("/api/teste/inesperado")), "/api/teste/inesperado");
    }

    @Test
    void tokenVencidoRespondeOMesmo401() throws Exception {
        when(jwtService.extractUsername("vencido"))
                .thenThrow(new ExpiredJwtException(null, null, "expirado"));

        esperaNaoAutenticado(
                mvc.perform(get("/api/teste/inesperado").header("Authorization", "Bearer vencido")),
                "/api/teste/inesperado");
    }

    @Test
    void perfilSemPermissaoContinua403ComErrorProprio() throws Exception {
        mvc.perform(get("/api/teste/perfil").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PERFIL_NAO_PERMITIDO"));
    }

    @Test
    void accessDeniedDoSpringSecurityContinua403ENao500() throws Exception {
        mvc.perform(get("/api/teste/acesso-negado").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void despachoDeErroNaoExigeAutenticacao() throws Exception {
        mvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/teste/inesperado"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void requisicaoDiretaAoErrorContinuaExigindoAutenticacao() throws Exception {
        esperaNaoAutenticado(mvc.perform(get("/error")), "/error");
    }

    private static void esperaNaoAutenticado(ResultActions resultado, String caminho) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.message").value("Sessão expirada ou inexistente. Entre novamente."))
                .andExpect(jsonPath("$.path").value("uri=" + caminho));
    }

    @RestController
    static class Endpoints {
        @GetMapping("/api/teste/inesperado")
        String inesperado() {
            throw new IllegalStateException("detalhe interno que não pode sair na resposta");
        }

        @GetMapping("/api/teste/perfil")
        String perfil() {
            throw new PerfilNaoPermitidoException("Seu perfil não permite consultar este registro");
        }

        @GetMapping("/api/teste/acesso-negado")
        String acessoNegado() {
            throw new AccessDeniedException("negado");
        }
    }
}
