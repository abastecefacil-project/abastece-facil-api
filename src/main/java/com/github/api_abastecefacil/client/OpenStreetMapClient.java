package com.github.api_abastecefacil.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "openstreetmap-client", url = "${openstreetmap-api.url}")
public interface OpenStreetMapClient {

    @GetMapping(value = "/search", consumes = "application/json")
    List<Map<String, Object>> search(
            @RequestParam("q") String query,
            @RequestParam("format") String format,
            @RequestParam("addressdetails") int addressDetails,
            @RequestParam("limit") int limit,
            @RequestHeader("User-Agent") String userAgent
    );
}

