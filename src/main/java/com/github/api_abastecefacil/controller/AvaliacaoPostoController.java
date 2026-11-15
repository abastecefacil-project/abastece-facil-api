package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.gasStation.*;
import com.github.api_abastecefacil.service.AvaliacaoPostoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/gas-stations")
public class AvaliacaoPostoController {
    private final AvaliacaoPostoService service;

    public AvaliacaoPostoController(AvaliacaoPostoService service) {
        this.service = service;
    }

    @GetMapping("/avaliacoes/resumos")
    public List<ResumoAvaliacaoPosto> resumos() {
        return service.resumos();
    }

    @GetMapping("/{postoId}/avaliacoes")
    public AvaliacoesPostoResponse consultar(@PathVariable Long postoId, @RequestParam(defaultValue = "0") int page) {
        return service.consultar(postoId, page);
    }

    @PutMapping("/{postoId}/avaliacoes")
    public AvaliacoesPostoResponse salvar(@PathVariable Long postoId, @Valid @RequestBody AvaliacaoPostoRequest request) {
        return service.salvar(postoId, request);
    }

    @DeleteMapping("/{postoId}/avaliacoes")
    public AvaliacoesPostoResponse remover(@PathVariable Long postoId) {
        return service.remover(postoId);
    }
}
