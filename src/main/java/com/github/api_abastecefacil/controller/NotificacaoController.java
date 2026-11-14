package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.notificacao.NotificacoesResponse;
import com.github.api_abastecefacil.service.NotificacaoService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificacoes/neo")
public class NotificacaoController {
    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) { this.service = service; }

    @GetMapping
    public NotificacoesResponse listar(@PageableDefault(size = 10) Pageable pageable) {
        return service.listar(pageable);
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarLida(@PathVariable Long id) {
        service.marcarLida(id);
        return ResponseEntity.noContent().build();
    }
}
