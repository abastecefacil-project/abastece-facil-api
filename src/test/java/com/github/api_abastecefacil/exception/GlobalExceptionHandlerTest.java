package com.github.api_abastecefacil.exception;

import com.github.api_abastecefacil.dto.handler.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("uri=/test");
    }

    @Test
    void handleUserAlreadyExistsException_ShouldReturn409Conflict() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("Usuário já existe");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUserAlreadyExistsException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Usuário já existe");
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
    }

    @Test
    void handleNotFoundException_ShouldReturn404NotFound() {
        NotFoundException ex = new NotFoundException("Recurso não encontrado");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNotFoundException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Recurso não encontrado");
    }

    @Test
    void handleInvalidLoginException_ShouldReturn401Unauthorized() {
        InvalidLoginException ex = new InvalidLoginException("Credenciais inválidas");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadCredentialsException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void handleCarAlreadyExistsException_ShouldReturn409Conflict() {
        CarAlreadyExistsException ex = new CarAlreadyExistsException("Carro com esta placa já existe");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCarAlreadyExistsException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Carro com esta placa já existe");
    }

    @Test
    void handleTokenInvalidoException_ShouldReturn410Gone() {
        TokenInvalidoException ex = new TokenInvalidoException("Token inválido ou expirado");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTokenInvalidoException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("TOKEN_INVALIDO");
        assertThat(response.getBody().message()).isEqualTo("Token inválido ou expirado");
    }

    @Test
    void handleEnvioEmailException_ShouldReturn502BadGateway() {
        // 502 e nao 500: a requisicao estava correta, quem falhou foi o provedor de
        // e-mail. Acompanha o default do handleFeignException, que ja mapeia falha de
        // servico externo para BAD_GATEWAY.
        EnvioEmailException ex = new EnvioEmailException("Não foi possível enviar o e-mail");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleEnvioEmailException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("EMAIL_NAO_ENVIADO");
        assertThat(response.getBody().message()).isEqualTo("Não foi possível enviar o e-mail");
    }

    @Test
    void handleEnvioEmailException_ShouldNotLeakTheChainedCause_ToTheResponse() {
        // A causa existe para o stack trace do log. O que chega ao cliente e so a
        // mensagem generica.
        EnvioEmailException ex = new EnvioEmailException(
                "Não foi possível enviar o e-mail",
                new IllegalStateException("Connection refused: api.resend.com:443"));

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleEnvioEmailException(ex, webRequest);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("api.resend.com");
    }

    @Test
    void handleCoordinatesNotFoundException_ShouldReturn400BadRequest() {
        CoordinatesNotFoundException ex = new CoordinatesNotFoundException("Coordenadas não encontradas");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCoordinatesNotFoundException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("COORDINATES_NOT_FOUND");
    }
}
