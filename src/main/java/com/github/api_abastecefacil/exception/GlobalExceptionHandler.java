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

    /**
     * Primeiro 403 do projeto, junto de {@link #handleRegionalNaoPermitidaException}.
     *
     * <p>As duas rejeicoes de autorizacao tem "error" distintos de proposito, e nao um
     * FORBIDDEN generico: o S5 precisa dizer ao gestor se o que barrou foi o perfil que
     * ele tentou criar ou a regional, porque a acao corretiva e diferente em cada caso.
     * O ErrorResponse so carrega status, error, message e path, entao esse campo e o
     * unico discriminador programatico disponivel.
     *
     * <p>Nota de arquitetura: a autorizacao deste fluxo mora no UserService, nao em
     * @PreAuthorize, justamente para poder passar por aqui. O 403 do Spring Security e
     * lancado pelo ExceptionTranslationFilter, fora do @ControllerAdvice, e sairia sem
     * ErrorResponse e sem o campo error. Ver §6 do CLAUDE.md.
     */
    @ExceptionHandler(PerfilNaoPermitidoException.class)
    public ResponseEntity<ErrorResponse> handlePerfilNaoPermitidoException(
            PerfilNaoPermitidoException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "PERFIL_NAO_PERMITIDO",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(RegionalNaoPermitidaException.class)
    public ResponseEntity<ErrorResponse> handleRegionalNaoPermitidaException(
            RegionalNaoPermitidaException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "REGIONAL_NAO_PERMITIDA",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * 409, como as outras colisoes de unicidade do projeto. O "error" e proprio, e nao
     * CONFLICT como em UserAlreadyExists, porque o formulario de cadastro do S5 precisa
     * apontar o campo certo: e-mail e matricula sao dois inputs distintos e o gestor
     * precisa saber qual dos dois refazer.
     */
    @ExceptionHandler(MatriculaDuplicadaException.class)
    public ResponseEntity<ErrorResponse> handleMatriculaDuplicadaException(
            MatriculaDuplicadaException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "MATRICULA_DUPLICADA",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * 400: o endereco recebido nao pertence a nenhum dominio corporativo autorizado.
     *
     * <p>Nao e 403. Quem esta autenticado e autorizado e o gestor, e ele pode criar o
     * usuario; o que esta errado e o dado. O caso predominante e erro de digitacao no
     * formulario, e por isso a mensagem lista os dominios aceitos em vez de so recusar.
     */
    @ExceptionHandler(DominioEmailNaoPermitidoException.class)
    public ResponseEntity<ErrorResponse> handleDominioEmailNaoPermitidoException(
            DominioEmailNaoPermitidoException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "DOMINIO_EMAIL_NAO_PERMITIDO",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 409: conflito de estado, nao payload invalido. O pedido de reenvio esta bem formado
     * e quem pediu esta autorizado -- o que impede e a conta ja ter senha, e nenhuma
     * mudanca no corpo da requisicao mudaria isso.
     *
     * <p>Reenviar convite para conta ja ativa entregaria a quem pediu o reenvio um link
     * capaz de trocar a senha de outra pessoa. Quem esqueceu a senha usa a recuperacao
     * (S4), que exige acesso a caixa de e-mail do dono.
     */
    @ExceptionHandler(SenhaJaDefinidaException.class)
    public ResponseEntity<ErrorResponse> handleSenhaJaDefinidaException(
            SenhaJaDefinidaException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "SENHA_JA_DEFINIDA",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * 400: a senha escolhida nao atende a politica. Codigo proprio, e nao BAD_REQUEST
     * generico, para o formulario do S6 poder destacar o campo de senha -- BAD_REQUEST
     * tambem sai de validacao de bean, matricula e telefone.
     *
     * <p>Duas regras compartilham este codigo (comprimento/composicao e conter dados
     * pessoais) porque a acao corretiva e a mesma: escolher outra senha. A mensagem
     * distingue as duas; ela NUNCA inclui a senha recebida.
     */
    @ExceptionHandler(SenhaFracaException.class)
    public ResponseEntity<ErrorResponse> handleSenhaFracaException(
            SenhaFracaException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "SENHA_FRACA",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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

    /**
     * 502 Bad Gateway: a requisicao estava correta, quem falhou foi o provedor de e-mail.
     * Acompanha o precedente do {@link #handleFeignException}, cujo {@code default} ja
     * mapeia falha de servico externo para BAD_GATEWAY.
     *
     * <p>O "error" proprio segue a razao registrada em
     * {@link #handlePasswordNotSetException}: o ErrorResponse so carrega status, error,
     * message e path, entao esse campo e o unico discriminador programatico que o
     * frontend tem para diferenciar "o convite nao saiu, tente de novo" de qualquer
     * outro 502.
     *
     * <p>A mensagem e uma constante generica. Status HTTP do provedor, corpo da resposta
     * e causa encadeada ficam so no log -- ver ResendEnviadorEmail.
     *
     * <p>Ainda nao alcancavel por endpoint nenhum: o M3 entrega so o canal de envio. O
     * status deve ser reconfirmado quando existir rota que dispare envio (S2 e S4).
     */
    @ExceptionHandler(EnvioEmailException.class)
    public ResponseEntity<ErrorResponse> handleEnvioEmailException(
            EnvioEmailException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "EMAIL_NAO_ENVIADO",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
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
