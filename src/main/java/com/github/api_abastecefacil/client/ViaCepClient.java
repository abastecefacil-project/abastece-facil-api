package com.github.api_abastecefacil.client;

import com.github.api_abastecefacil.dto.cep.CepResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "viaCepClient", url = "${viacep-api.url}")
public interface ViaCepClient {
    @GetMapping("{cep}/json")
    CepResponse getCepInfo(@PathVariable("cep") String cep);
}
