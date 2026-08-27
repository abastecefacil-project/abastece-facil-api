package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.regional.RegionalResponse;
import com.github.api_abastecefacil.service.RegionalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RegionalController {

    private final RegionalService regionalService;

    public RegionalController(RegionalService regionalService) {
        this.regionalService = regionalService;
    }

    @GetMapping("regionais")
    public ResponseEntity<Page<RegionalResponse>> getRegionais(
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(regionalService.getRegionais(pageable));
    }

    @GetMapping("regionais/{regionalId}")
    public ResponseEntity<RegionalResponse> getRegionalById(@PathVariable Long regionalId) {
        return ResponseEntity.ok(regionalService.getRegionalById(regionalId));
    }
}
