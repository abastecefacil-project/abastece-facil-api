package com.github.api_abastecefacil.exception;

import com.github.api_abastecefacil.dto.handler.ErrorResponse;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            NotFoundException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            InvalidLoginException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Mesmo status de InvalidLoginException, mas com "error" proprio: o ErrorResponse
     * so carrega status, error, message e path, entao o campo error e a unica forma de
     * o frontend distinguir "ative sua conta" de "senha errada" programaticamente.
     * Precedente: CoordinatesNotFoundException, que tambem foge do nome do status.
     */
    @ExceptionHandler(PasswordNotSetException.class)
    public ResponseEntity<ErrorResponse> handlePasswordNotSetException(
            PasswordNotSetException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "PASSWORD_NOT_SET",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * 410 Gone, e nao 400: o caso predominante nao e uma requisicao malformada, e um
     * token que existiu e nao vale mais -- consumido, expirado ou substituido por um
     * reenvio. O status comunica isso melhor.
     *
     * <p>O "error" foge do nome do status pelo mesmo motivo de
     * {@link #handlePasswordNotSetException}: o ErrorResponse so carrega status, error,
     * message e path, entao esse campo e o unico discriminador programatico que o
     * frontend tem para diferenciar "link expirado, peca outro" de qualquer outro 410.
     *
     * <p>A mensagem e a mesma para as quatro rejeicoes possiveis (inexistente, usado,
     * expirado, finalidade divergente), de proposito. Ver TokenAcessoConstants.
     *
     * <p>Ainda nao alcancavel por endpoint nenhum: o M2 entrega so dominio e
     * persistencia. O status deve ser reconfirmado no M3, quando existir rota
     * consumindo o token.
     */
    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleTokenInvalidoException(
            TokenInvalidoException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.GONE.value(),
                "TOKEN_INVALIDO",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.GONE).body(error);
    }

    @ExceptionHandler(InvalidUserDataException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUserDataException(
            InvalidUserDataException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(UserAlreadyDeletedException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyDeletedException(
            UserAlreadyDeletedException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(CarAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCarAlreadyExistsException(
            CarAlreadyExistsException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(CarAlreadyDeletedException.class)
    public ResponseEntity<ErrorResponse> handleCarAlreadyDeletedException(
            CarAlreadyDeletedException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(CarDecommissionedUpdateException.class)
    public ResponseEntity<ErrorResponse> handleCarDecommissionedUpdateException(
            CarDecommissionedUpdateException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(CarUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCarAlreadyInUseException(
            CarUnavailableException ex, WebRequest request) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(GasStationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlerGasStationAlreadyExistsException(
            GasStationAlreadyExistsException ex, WebRequest request) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                "Erro de validação: " + errorMessage,
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(CoordinatesNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCoordinatesNotFoundException(
            CoordinatesNotFoundException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "COORDINATES_NOT_FOUND",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(
            FeignException ex, WebRequest request) {

        HttpStatus status = switch (ex.status()) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_GATEWAY;
        };

        ErrorResponse error = ErrorResponse.of(
                status.value(),
                status.name(),
                "Erro ao consultar serviço externo: " + ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(status).body(error);
    }

}
