package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.car.CarResponse;
import com.github.api_abastecefacil.dto.car.CreateCarRequest;
import com.github.api_abastecefacil.dto.car.UpdateCarRequest;
import com.github.api_abastecefacil.exception.CarAlreadyExistsException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.mapper.CarMapper;
import com.github.api_abastecefacil.model.Car;
import com.github.api_abastecefacil.repository.CarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock
    private CarRepository carRepository;

    @Mock
    private CarMapper carMapper;

    @Mock
    private AutorizacaoOperacional autorizacaoOperacional;

    @InjectMocks
    private CarService carService;

    private Car car;
    private CarResponse carResponse;

    @BeforeEach
    void setUp() {
        car = new Car()
                .setId(1L)
                .setLicensePlate("ABC1234")
                .setModel("Civic")
                .setActive(true)
                .setCreatedAt(LocalDateTime.now());

        carResponse = new CarResponse(1L, "ABC1234", "Civic", true, LocalDateTime.now(), null);
    }

    @Test
    void createCar_ShouldCreateCarSuccessfully() {
        CreateCarRequest request = new CreateCarRequest("ABC1234", "Civic");
        when(carRepository.existsCarByLicensePlate("ABC1234")).thenReturn(false);
        when(carMapper.toEntity(request)).thenReturn(car);
        when(carRepository.save(car)).thenReturn(car);
        when(carMapper.toResponse(car)).thenReturn(carResponse);

        CarResponse response = carService.createCar(request);

        assertThat(response).isNotNull();
        assertThat(response.licensePlate()).isEqualTo("ABC1234");
        verify(carRepository).save(car);
    }

    @Test
    void createCar_ShouldThrowCarAlreadyExistsException_WhenPlateExists() {
        CreateCarRequest request = new CreateCarRequest("ABC1234", "Civic");
        when(carRepository.existsCarByLicensePlate("ABC1234")).thenReturn(true);

        assertThrows(CarAlreadyExistsException.class, () -> carService.createCar(request));
        verify(carRepository, never()).save(any());
    }

    @Test
    void getCarById_ShouldReturnCar_WhenIdExists() {
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(carMapper.toResponse(car)).thenReturn(carResponse);

        CarResponse response = carService.getCarById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getCarById_ShouldThrowNotFoundException_WhenIdDoesNotExist() {
        when(carRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> carService.getCarById(99L));
    }

    @Test
    void updateCar_ShouldUpdateCarSuccessfully() {
        UpdateCarRequest request = new UpdateCarRequest("XYZ9876", "Corolla", false);
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(carRepository.findByLicensePlate("XYZ9876")).thenReturn(Optional.empty());
        when(carRepository.save(car)).thenReturn(car);
        when(carMapper.toResponse(car)).thenReturn(carResponse);

        CarResponse response = carService.updateCar(1L, request);

        assertThat(response).isNotNull();
        verify(carRepository).save(car);
    }

    @Test
    void updateCar_ShouldThrowCarAlreadyExistsException_WhenPlateBelongsToAnotherCar() {
        Car anotherCar = new Car().setId(2L).setLicensePlate("XYZ9876");
        UpdateCarRequest request = new UpdateCarRequest("XYZ9876", "Corolla", true);

        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(carRepository.findByLicensePlate("XYZ9876")).thenReturn(Optional.of(anotherCar));

        assertThrows(CarAlreadyExistsException.class, () -> carService.updateCar(1L, request));
    }

    @Test
    void deleteCar_ShouldDeleteCarSuccessfully() {
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));

        carService.deleteCar(1L);

        verify(carRepository).delete(car);
    }

    @Test
    void getCarsByFilters_ShouldReturnPagedCars() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Car> carPage = new PageImpl<>(List.of(car));

        when(carRepository.findByFilters("Civic", true, pageable)).thenReturn(carPage);
        when(carMapper.toResponse(car)).thenReturn(carResponse);

        Page<CarResponse> result = carService.getCarsByFilters("Civic", true, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    // ------------------------------------------------- P0.4: autorizacao por perfil

    /**
     * A guarda e a PRIMEIRA instrucao de cada metodo protegido, entao o request pode ser
     * nulo: se algo for tocado antes dela, o teste quebra com NullPointerException em vez
     * de passar. Mesmo principio do item 22 do CLAUDE.md -- autorizar vem antes de validar.
     */
    @Test
    void createCar_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class, () -> carService.createCar(null));

        verifyNoInteractions(carRepository, carMapper);
    }

    @Test
    void updateCar_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class, () -> carService.updateCar(1L, null));

        verifyNoInteractions(carRepository, carMapper);
    }

    @Test
    void deleteCar_ShouldAuthorizeBeforeTouchingAnythingElse() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarEscrita();

        assertThrows(PerfilNaoPermitidoException.class, () -> carService.deleteCar(1L));

        verifyNoInteractions(carRepository, carMapper);
    }

    @Test
    void getCarById_ShouldRequireConsulta_NotEscrita() {
        // Consulta e escrita sao metodos distintos de proposito: se a leitura passar a
        // chamar autorizarEscrita, este teste quebra.
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarConsulta();

        assertThrows(PerfilNaoPermitidoException.class, () -> carService.getCarById(1L));

        verifyNoInteractions(carRepository, carMapper);
    }

    @Test
    void getCarsByFilters_ShouldRequireConsulta() {
        doThrow(new PerfilNaoPermitidoException("negado"))
                .when(autorizacaoOperacional).autorizarConsulta();

        assertThrows(PerfilNaoPermitidoException.class,
                () -> carService.getCarsByFilters(null, null, PageRequest.of(0, 10)));

        verifyNoInteractions(carRepository, carMapper);
    }

    @Test
    void createCar_ShouldAuthorizeOnTheHappyPathToo() {
        // Sem este, um caminho feliz que pulasse a guarda passaria despercebido.
        CreateCarRequest request = new CreateCarRequest("ABC1234", "Civic");
        when(carRepository.existsCarByLicensePlate("ABC1234")).thenReturn(false);
        when(carMapper.toEntity(request)).thenReturn(car);
        when(carRepository.save(car)).thenReturn(car);
        when(carMapper.toResponse(car)).thenReturn(carResponse);

        carService.createCar(request);

        verify(autorizacaoOperacional).autorizarEscrita();
    }

}
