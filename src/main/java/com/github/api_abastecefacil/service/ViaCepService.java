package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.ViaCepClient;
import com.github.api_abastecefacil.dto.cep.CepResponse;
import org.springframework.stereotype.Service;

@Service
public class ViaCepService {

    private final ViaCepClient cepApiClient;

    public ViaCepService(ViaCepClient cepApiClient) {
        this.cepApiClient = cepApiClient;
    }

    public CepResponse getCepInfo(String cep) {
        return cepApiClient.getCepInfo(cep);
    }
}
