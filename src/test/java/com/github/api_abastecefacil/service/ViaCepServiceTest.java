package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.client.ViaCepClient;
import com.github.api_abastecefacil.dto.cep.CepResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViaCepServiceTest {

    @Mock
    private ViaCepClient viaCepClient;

    @InjectMocks
    private ViaCepService viaCepService;

    @Test
    void getCepInfo_ShouldReturnCepResponse() {
        CepResponse mockResponse = new CepResponse("01001-000", "Praça da Sé", "lado ímpar", "", "Sé", "São Paulo", "SP", "São Paulo", "Sudeste", "3550308", "1004", "11", "7107");
        when(viaCepClient.getCepInfo("01001-000")).thenReturn(mockResponse);

        CepResponse response = viaCepService.getCepInfo("01001-000");

        assertThat(response).isNotNull();
        assertThat(response.cep()).isEqualTo("01001-000");
        assertThat(response.localidade()).isEqualTo("São Paulo");
    }
}
