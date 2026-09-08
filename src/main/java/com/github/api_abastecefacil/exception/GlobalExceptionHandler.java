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
     * <p>Nota de arquitetura: a autorizacao mora no servico -- UserService para
     * /api/users/**, AutorizacaoOperacional para posto, veiculo e ocorrencia (P0.4) --
     * e nao em @PreAuthorize.
     *
     * <p>O motivo mudou de forma no P0.4, que mediu a alternativa: o 403 de um
     * @PreAuthorize E alcancavel por este @ControllerAdvice, porque e lancado dentro do
     * dispatch. Quem fica de fora e o 403 da cadeia de filtros (requisicao sem token),
     * lancado pelo ExceptionTranslationFilter. O que sustenta a decisao hoje e que
     * nenhum teste do projeto sobe contexto Spring, entao anotacao de autorizacao ficaria
     * sem cobertura. Ver §5 do CLAUDE.md, "Por que nao foi @PreAuthorize".
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
     * 403, e nao 400: o pedido esta bem formado e o autor esta autenticado; o que o barra
     * e quem ele e em relacao ao alvo. O "error" e proprio pelo mesmo motivo dos dois
     * acima -- a acao corretiva e especifica, e o frontend precisa poder dizer "peca a um
     * administrador" em vez de "voce nao pode".
     */
    @ExceptionHandler(SenhaDeTerceiroException.class)
    public ResponseEntity<ErrorResponse> handleSenhaDeTerceiroException(
            SenhaDeTerceiroException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "SENHA_DE_TERCEIRO",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * 403, e nao 400: e regra de autorizacao, nao dado invalido -- o id do path existe e
     * a requisicao esta correta. O "error" e proprio porque nao ha acao corretiva nenhuma
     * do lado de quem chamou: outra pessoa precisa executar a exclusao, e o frontend deve
     * dizer isso em vez de sugerir uma nova tentativa.
     */
    @ExceptionHandler(AutoExclusaoNaoPermitidaException.class)
    public ResponseEntity<ErrorResponse> handleAutoExclusaoNaoPermitidaException(
            AutoExclusaoNaoPermitidaException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "AUTO_EXCLUSAO_NAO_PERMITIDA",
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
     * <p><b>Continua nao alcancavel por endpoint nenhum, agora por escolha e nao por
     * ausencia de rota.</b> O S2b1 e o S4 disparam envio, mas os dois capturam a excecao
     * no {@code EnvioAcessoService}: o cadastro sinaliza a falha no campo
     * {@code conviteEnviado} em vez de derrubar a criacao (§6, item 24), e a recuperacao
     * ja respondeu ao cliente antes de o envio comecar. Este handler existe como rede de
     * seguranca -- sem ele, um caminho futuro que deixasse a excecao escapar viraria 500
     * cru, porque nao ha fallback Exception.class.
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

    /**
     * 429 Too Many Requests: o primeiro do projeto, introduzido pelo limite de
     * solicitacoes da recuperacao de senha (S4).
     *
     * <p>429 e nao 403: nao ha nada de errado com o solicitante nem com a requisicao, que
     * seria aceita alguns minutos depois. O 429 e o unico status que comunica "correto,
     * mas agora nao" -- e e o que permite ao frontend exibir "aguarde" em vez de "sem
     * permissao".
     *
     * <p>A mensagem e generica e identica para os dois limites, por e-mail e por IP. Dizer
     * qual estourou revelaria que aquele endereco vinha sendo tentado, o que reintroduziria
     * pela porta do erro a enumeracao de contas que o resto do fluxo evita. Pelo mesmo
     * motivo nao ha cabecalho {@code Retry-After}: o prazo exato ate a proxima tentativa
     * depende de quando as anteriores aconteceram, e devolve-lo seria contar parte dessa
     * historia.
     */
    @ExceptionHandler(LimiteSolicitacoesExcedidoException.class)
    public ResponseEntity<ErrorResponse> handleLimiteSolicitacoesExcedidoException(
            LimiteSolicitacoesExcedidoException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "LIMITE_SOLICITACOES_EXCEDIDO",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
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
