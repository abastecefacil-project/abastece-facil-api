package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.gasStation.AvaliacaoNeoRequest;
import com.github.api_abastecefacil.dto.gasStation.AvaliacaoNeoResponse;
import com.github.api_abastecefacil.service.AvaliacaoNeoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gas-stations/{postoId}/neo")
public class AvaliacaoNeoController {
    private final AvaliacaoNeoService service;

    public AvaliacaoNeoController(AvaliacaoNeoService service) {
        this.service = service;
    }

    @GetMapping
    public AvaliacaoNeoResponse consultar(@PathVariable Long postoId) {
        return service.consultar(postoId);
    }

    @PutMapping
    public AvaliacaoNeoResponse salvar(@PathVariable Long postoId, @Valid @RequestBody AvaliacaoNeoRequest request) {
        return service.salvar(postoId, request.passouNeo());
    }

    @DeleteMapping
    public AvaliacaoNeoResponse remover(@PathVariable Long postoId) {
        return service.remover(postoId);
    }
}
