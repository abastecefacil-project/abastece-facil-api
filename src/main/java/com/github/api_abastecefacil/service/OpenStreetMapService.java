package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.OpenStreetMapClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.*;

@Service
public class OpenStreetMapService {

    private final OpenStreetMapClient openStreetMapClient;
    private final String userAgent;

    public OpenStreetMapService(
            OpenStreetMapClient openStreetMapClient,
            @Value("${openstreetmap-api.user-agent}") String userAgent
    ) {
        this.openStreetMapClient = openStreetMapClient;
        this.userAgent = userAgent;
    }

    public Optional<Coordinates> getCoordinates(String query) {
        List<Map<String, Object>> results = searchLocation(query);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return extractCoordinatesFromFirstResult(results);
    }

    private List<Map<String, Object>> searchLocation(String query) {
        return openStreetMapClient.search(
                query,
                FORMAT_JSON,
                DEFAULT_LIMIT,
                DEFAULT_ADDRESS_DETAIL,
                userAgent
        );
    }

    private Optional<Coordinates> extractCoordinatesFromFirstResult(List<Map<String, Object>> results) {
        Map<String, Object> firstLocation = results.get(0);
        BigDecimal latitude = parseCoordinate(firstLocation.get(LATITUDE_KEY));
        BigDecimal longitude = parseCoordinate(firstLocation.get(LONGITUDE_KEY));
        return Optional.of(new Coordinates(latitude, longitude));
    }

    private BigDecimal parseCoordinate(Object value) {
        if (value instanceof String stringValue) {
            return new BigDecimal(stringValue);
        }
        throw new IllegalArgumentException("Formato de coordenada inválido: " + value);
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}