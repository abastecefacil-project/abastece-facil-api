package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.dashboard.DashboardResponse;
import com.github.api_abastecefacil.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getResumo() {
        return ResponseEntity.ok(dashboardService.getResumo());
    }
}
