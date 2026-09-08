package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.DefinicaoSenhaRequest;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RecuperacaoRequest;
import com.github.api_abastecefacil.dto.auth.RecuperacaoResponse;
import com.github.api_abastecefacil.dto.auth.TokenValidacaoResponse;
import com.github.api_abastecefacil.service.AuthService;
import com.github.api_abastecefacil.util.IpSolicitante;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todas as rotas daqui são públicas: {@code /api/auth/**} já é {@code permitAll()} no
 * {@code SecurityConfig} desde o início do projeto, e as três rotas de recuperação do S4
 * nasceram públicas por isso — sem nenhuma alteração de configuração de segurança, e sem
 * abrir nada além delas. Acrescentar matchers explícitos seria redundante e sugeriria que
 * a regra mudou.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Sonda do link de ativação: diz apenas se o token serve, para o frontend escolher
     * entre o formulário de senha e a mensagem de link expirado.
     *
     * <p><b>Responde 200 mesmo para token inválido</b> — link expirado é desfecho normal
     * do fluxo, não erro, e obrigar o frontend a tratar exceção para a tela mais comum
     * seria pior. O POST abaixo continua respondendo 410. Ver §5 do CLAUDE.md.
     *
     * <p>O token vir em query string é exceção consciente: este endpoint não consome
     * nada, e é exatamente o que o navegador entrega ao abrir o link do e-mail. A senha,
     * essa, só trafega no corpo do POST.
     */
    @GetMapping("/ativacao/validar")
    public ResponseEntity<TokenValidacaoResponse> validarAtivacao(@RequestParam String token) {
        return ResponseEntity.ok(authService.validarTokenAtivacao(token));
    }

    @PostMapping("/ativacao")
    public ResponseEntity<AuthResponse> ativar(@Valid @RequestBody DefinicaoSenhaRequest request) {
        return ResponseEntity.ok(authService.ativarConta(request));
    }

    /**
     * Solicita a redefinição de senha.
     *
     * <p><b>Responde 200 com o mesmo corpo em todos os casos</b> — e-mail cadastrado,
     * e-mail inexistente, conta inativa, conta que nunca definiu senha. O único desfecho
     * diferente é o 429 do limite de solicitações, que vale igualmente para qualquer
     * e-mail e por isso também não diz se a conta existe.
     *
     * <p>O {@code HttpServletRequest} entra só para o IP, que alimenta o limite por
     * endereço e a auditoria de {@code tokens_acesso}. É o segundo controller do projeto a
     * conhecer a requisição HTTP, pelo mesmo motivo do primeiro.
     */
    @PostMapping("/recuperacao")
    public ResponseEntity<RecuperacaoResponse> solicitarRecuperacao(
            @Valid @RequestBody RecuperacaoRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                authService.solicitarRecuperacao(request, IpSolicitante.extrair(httpRequest)));
    }

    /**
     * Sonda do link de recuperação, gêmea da sonda de ativação: 200 sempre, e não consome
     * o token. Recarregar a página não queima o link.
     */
    @GetMapping("/recuperacao/validar")
    public ResponseEntity<TokenValidacaoResponse> validarRecuperacao(@RequestParam String token) {
        return ResponseEntity.ok(authService.validarTokenRecuperacao(token));
    }

    /**
     * Grava a senha nova e devolve a pessoa já autenticada, como a ativação faz.
     *
     * <p>A senha vem no corpo, nunca em query string. Um token de ativação apresentado
     * aqui responde 410, como qualquer token inválido — a finalidade faz parte do
     * predicado de validade.
     */
    @PostMapping("/recuperacao/confirmar")
    public ResponseEntity<AuthResponse> confirmarRecuperacao(
            @Valid @RequestBody DefinicaoSenhaRequest request) {
        return ResponseEntity.ok(authService.redefinirSenha(request));
    }
}
