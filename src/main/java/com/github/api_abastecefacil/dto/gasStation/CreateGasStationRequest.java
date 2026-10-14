package com.github.api_abastecefacil.dto.gasStation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * @param latitude  opcional. Com {@code longitude}, é usada no lugar da geocodificação — para o
 *                  posto que o Nominatim não localiza. As duas vêm juntas ou nenhuma
 * @param longitude opcional, ver {@code latitude}
 */
public record CreateGasStationRequest(

        @NotBlank(message = "Nome é obrigatório")
        String name,

        String fantasyName,

        @NotBlank(message = "CNPJ é obrigatório")
        String cnpj,

        @NotBlank(message = "CEP é obrigatório")
        String cep,

        @NotBlank(message = "Bairro é obrigatório")
        String district,

        @NotBlank(message = "Endereço é obrigatório")
        String address,

        @NotBlank(message = "Estado é obrigatório")
        String state,
        @NotBlank(message = "Cidade é obrigatória")
        String city,

        String phone,

        String businessHours,

        @DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90")
        @DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180")
        @DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180")
        BigDecimal longitude

) {

    /**
     * O Hibernate Validator trata este método como propriedade, então a violação chega ao
     * {@code GlobalExceptionHandler} como {@code FieldError}, igual às demais.
     */
    @JsonIgnore
    @AssertTrue(message = "Latitude e longitude devem ser informadas juntas")
    public boolean isCoordenadasCompletas() {
        return (latitude == null) == (longitude == null);
    }

    @JsonIgnore
    public boolean coordenadasInformadas() {
        return latitude != null && longitude != null;
    }
}
