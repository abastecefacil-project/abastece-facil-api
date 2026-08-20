package com.github.api_abastecefacil.dto.incident;

import java.util.List;

public record IncidentDashboardResponse(
        Long total,
        List<IncidentResponse> latestIncidents
) {
}
