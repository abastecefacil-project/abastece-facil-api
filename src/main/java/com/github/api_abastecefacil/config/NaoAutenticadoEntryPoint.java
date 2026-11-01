package com.github.api_abastecefacil.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.api_abastecefacil.dto.handler.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Resposta de toda requisição que chega sem autenticação válida a uma rota protegida: sem
 * token, token vencido, malformado ou com assinatura inválida, e usuário desativado depois
 * da emissão do token.
 *
 * <p><b>401, e não 403.</b> Até aqui a aplicação respondia 403 com corpo vazio, o
 * {@code Http403ForbiddenEntryPoint} padrão do Spring Security. O frontend precisa distinguir
 * "sessão expirada, vá para o login" de "autenticado sem permissão", e o 403 fica reservado
 * para o segundo caso.
 *
 * <p>O corpo é escrito aqui, e não com {@code sendError}: {@code sendError} dispararia o
 * despacho para {@code /error}, e o {@code BasicErrorController} trocaria este corpo pelo
 * formato padrão do Spring Boot.
 *
 * <p>Não é bean de propósito. Os testes {@code @WebMvcTest} importam {@code SecurityConfig} e
 * {@code JwtAuthenticationFilter} pela lista explícita; um bean novo obrigaria cada um deles a
 * importá-lo também. Os dois instanciam o seu, e a classe não tem estado.
 */
public class NaoAutenticadoEntryPoint implements AuthenticationEntryPoint {

    static final String NAO_AUTENTICADO_MESSAGE = "Sessão expirada ou inexistente. Entre novamente.";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escrever(request, response);
    }

    /** Ponto único do corpo 401, usado também pelo filtro JWT quando o token não decodifica. */
    void escrever(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponse error = ErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "NAO_AUTENTICADO",
                NAO_AUTENTICADO_MESSAGE,
                "uri=" + request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JSON.writeValue(response.getOutputStream(), error);
    }
}
