package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.cep.CepResponse;
import com.github.api_abastecefacil.service.ViaCepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cep")
public class ViaCepController {

    private final ViaCepService viaCepService;

    public ViaCepController(ViaCepService viaCepService) {
        this.viaCepService = viaCepService;
    }

    @GetMapping("/info")
    public ResponseEntity<CepResponse> getCepInfo(@RequestParam String cep) {
        return ResponseEntity.ok(viaCepService.getCepInfo(cep));
    }

}
