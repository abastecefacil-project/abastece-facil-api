package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.config.JwtAuthenticationFilter;
import com.github.api_abastecefacil.config.SecurityConfig;
import com.github.api_abastecefacil.dto.car.CarResponse;
import com.github.api_abastecefacil.service.CarService;
import com.github.api_abastecefacil.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CarController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AvailableCarsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CarService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void consultaExigeLogin() throws Exception {
        mvc.perform(get("/api/cars/available")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void colaboradorPodeConsultarVeiculosDisponiveis() throws Exception {
        when(service.getAvailableCars()).thenReturn(List.of(
                new CarResponse(1L, "ABC1234", "Civic", true, null, null, null)));
        mvc.perform(get("/api/cars/available").with(user("colaborador").roles("COLABORADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("Civic"))
                .andExpect(jsonPath("$[0].licensePlate").value("ABC1234"));
    }
}
