package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.OpenStreetMapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenStreetMapServiceTest {

    @Mock
    private OpenStreetMapClient openStreetMapClient;

    private OpenStreetMapService openStreetMapService;

    @BeforeEach
    void setUp() {
        openStreetMapService = new OpenStreetMapService(openStreetMapClient, "TestAgent/1.0");
    }

    @Test
    void getCoordinates_ShouldReturnCoordinates_WhenResultExists() {
        Map<String, Object> location = new HashMap<>();
        location.put("lat", "-23.550520");
        location.put("lon", "-46.633308");

        when(openStreetMapClient.search(eq("São Paulo"), eq("json"), anyInt(), anyInt(), eq("TestAgent/1.0")))
                .thenReturn(List.of(location));

        Optional<OpenStreetMapService.Coordinates> result = openStreetMapService.getCoordinates("São Paulo");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(new BigDecimal("-23.550520"));
        assertThat(result.get().longitude()).isEqualTo(new BigDecimal("-46.633308"));
    }

    @Test
    void getCoordinates_ShouldReturnEmpty_WhenResultIsEmpty() {
        when(openStreetMapClient.search(eq("Unknown"), eq("json"), anyInt(), anyInt(), eq("TestAgent/1.0")))
                .thenReturn(Collections.emptyList());

        Optional<OpenStreetMapService.Coordinates> result = openStreetMapService.getCoordinates("Unknown");

        assertThat(result).isEmpty();
    }
}
