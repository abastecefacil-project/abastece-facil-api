package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.auth.AtivacaoRequest;
import com.github.api_abastecefacil.dto.auth.AtivacaoValidacaoResponse;
import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
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
    public ResponseEntity<AtivacaoValidacaoResponse> validarAtivacao(@RequestParam String token) {
        return ResponseEntity.ok(authService.validarTokenAtivacao(token));
    }

    @PostMapping("/ativacao")
    public ResponseEntity<AuthResponse> ativar(@Valid @RequestBody AtivacaoRequest request) {
        return ResponseEntity.ok(authService.ativarConta(request));
    }
}
