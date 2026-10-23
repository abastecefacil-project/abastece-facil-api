package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.gasStation.NotificacoesNeoResponse;
import com.github.api_abastecefacil.service.NotificacaoNeoService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificacoes/neo")
public class NotificacaoNeoController {
    private final NotificacaoNeoService service;

    public NotificacaoNeoController(NotificacaoNeoService service) { this.service = service; }

    @GetMapping
    public NotificacoesNeoResponse listar(@PageableDefault(size = 10) Pageable pageable) {
        return service.listar(pageable);
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarLida(@PathVariable Long id) {
        service.marcarLida(id);
        return ResponseEntity.noContent().build();
    }
}
