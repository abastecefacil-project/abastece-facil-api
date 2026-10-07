package com.github.api_abastecefacil.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

import static com.github.api_abastecefacil.constants.OpenStreetMapConstants.*;

/**
 * Os dois métodos vão ao mesmo {@code /search} do Nominatim, em modos que não se combinam:
 * {@link #search} em texto livre ({@code q}), {@link #searchEstruturada} por campos
 * ({@code street}, {@code city}, {@code state}, {@code country}). Mandar {@code q} junto com
 * os campos faz o Nominatim recusar a requisição.
 *
 * <p>Atenção à ordem dos {@code int}: {@code addressdetails} e {@code limit} são adjacentes e
 * valem os dois 1, então uma inversão não é acusada pelo compilador nem por teste que só
 * confira valores (§9, item 18 do CLAUDE.md).
 */
@FeignClient(name = "openstreetmap-client", url = "${openstreetmap-api.url}")
public interface OpenStreetMapClient {

    @GetMapping(value = "/search", consumes = "application/json")
    List<Map<String, Object>> search(
            @RequestParam(PARAM_QUERY) String query,
            @RequestParam(PARAM_FORMAT) String format,
            @RequestParam(PARAM_ADDRESS_DETAILS) int addressDetails,
            @RequestParam(PARAM_LIMIT) int limit,
            @RequestParam(PARAM_COUNTRY_CODES) String countryCodes,
            @RequestHeader(HEADER_USER_AGENT) String userAgent
    );

    @GetMapping(value = "/search", consumes = "application/json")
    List<Map<String, Object>> searchEstruturada(
            @RequestParam(PARAM_STREET) String street,
            @RequestParam(PARAM_CITY) String city,
            @RequestParam(PARAM_STATE) String state,
            @RequestParam(PARAM_COUNTRY) String country,
            @RequestParam(PARAM_FORMAT) String format,
            @RequestParam(PARAM_ADDRESS_DETAILS) int addressDetails,
            @RequestParam(PARAM_LIMIT) int limit,
            @RequestParam(PARAM_COUNTRY_CODES) String countryCodes,
            @RequestHeader(HEADER_USER_AGENT) String userAgent
    );
}
