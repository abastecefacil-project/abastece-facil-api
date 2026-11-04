package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.car.CreateCarRequest;
import com.github.api_abastecefacil.dto.car.UpdateCarRequest;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.exception.RegionalNaoPermitidaException;
import com.github.api_abastecefacil.mapper.CarMapper;
import com.github.api_abastecefacil.mapper.RegionalMapper;
import com.github.api_abastecefacil.model.*;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.RegionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CarRegionalAccessTest {
    private final CarRepository cars = mock(CarRepository.class);
    private final RegionalRepository regionais = mock(RegionalRepository.class);
    private final UsuarioAutenticadoProvider autenticado = mock(UsuarioAutenticadoProvider.class);
    private final AutorizacaoOperacional autorizacao = new AutorizacaoOperacional(autenticado);
    private final CarService service = new CarService(cars, new CarMapper(new RegionalMapper()),
            autorizacao, regionais, autenticado);
    private final Regional regional = new Regional().setId(1L).setNome("Joinville").setAtivo(true);
    private final User gestor = new User().setPerfil(Perfil.GESTOR_FROTA).setRegional(regional);

    @BeforeEach
    void autenticarGestor() {
        when(autenticado.obterUsuarioAutenticado()).thenReturn(gestor);
    }

    @Test
    void gestorCadastraNaSuaRegionalMesmoSemInformarNoPedido() {
        when(cars.save(any())).thenAnswer(call -> ((Car) call.getArgument(0)).setActive(true));
        var response = service.createCar(new CreateCarRequest("ABC1234", "Civic", null));
        assertThat(response.regional().id()).isEqualTo(1L);
        verifyNoInteractions(regionais);
    }

    @Test
    void gestorNaoCadastraEmRegionalForjada() {
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.createCar(new CreateCarRequest("ABC1234", "Civic", 2L)));
        verify(cars, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 2L)
    void gestorNaoConsultaEditaOuExcluiVeiculoDeOutraRegionalOuSemRegional(Long regionalId) {
        Car car = new Car().setId(10L).setModel("Original");
        if (regionalId != null) car.setRegional(new Regional().setId(regionalId));
        when(cars.findById(10L)).thenReturn(Optional.of(car));
        assertThrows(RegionalNaoPermitidaException.class, () -> service.getCarById(10L));
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.updateCar(10L, new UpdateCarRequest(null, "Alterado", false, 1L)));
        assertThrows(RegionalNaoPermitidaException.class, () -> service.deleteCar(10L));
        assertThat(car.getModel()).isEqualTo("Original");
        verify(cars, never()).save(any());
        verify(cars, never()).delete(any());
    }

    @Test
    void gestorConsultaEditaEExcluiVeiculoDaRegionalMasNaoTransfere() {
        Car car = new Car().setId(10L).setModel("Original").setRegional(regional).setActive(true);
        when(cars.findById(10L)).thenReturn(Optional.of(car));
        when(cars.save(car)).thenReturn(car);
        assertThat(service.getCarById(10L).regional().id()).isEqualTo(1L);
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.updateCar(10L, new UpdateCarRequest(null, "Indevido", null, 2L)));
        assertThat(car.getModel()).isEqualTo("Original");
        var response = service.updateCar(10L, new UpdateCarRequest(null, "Corolla", false, null));
        assertThat(response.model()).isEqualTo("Corolla");
        assertThat(response.active()).isFalse();
        assertThat(response.regional().id()).isEqualTo(1L);
        service.deleteCar(10L);
        verify(cars).delete(car);
    }

    @Test
    void listagemPaginadaEDisponiveisDoGestorSaoFiltradosNoBanco() {
        var page = PageRequest.of(0, 10);
        when(cars.findByFilters("Civic", false, 1L, page)).thenReturn(Page.empty(page));
        when(cars.findByFilters(null, true, 1L, Pageable.unpaged())).thenReturn(Page.empty());
        service.getCarsByFilters("Civic", false, page);
        service.getAvailableCars();
        verify(cars).findByFilters("Civic", false, 1L, page);
        verify(cars).findByFilters(null, true, 1L, Pageable.unpaged());
    }

    @Test
    void gestorSemRegionalNaoListaNemCadastra() {
        gestor.setRegional(null);
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.getCarsByFilters(null, null, PageRequest.of(0, 10)));
        assertThrows(RegionalNaoPermitidaException.class, () -> service.getAvailableCars());
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.createCar(new CreateCarRequest("ABC1234", "Civic", 1L)));
        verify(cars, never()).save(any());
    }

    @Test
    void administradorPodeVincularVeiculoAntigoETransferirEntreRegionais() {
        gestor.setPerfil(Perfil.ADMINISTRADOR).setRegional(null);
        Car car = new Car().setId(10L).setModel("Civic").setActive(true);
        when(cars.findById(10L)).thenReturn(Optional.of(car));
        when(cars.save(car)).thenReturn(car);
        when(regionais.findById(1L)).thenReturn(Optional.of(regional));
        assertThat(service.getCarById(10L).regional()).isNull();
        assertThat(service.updateCar(10L, new UpdateCarRequest(null, null, null, 1L))
                .regional().id()).isEqualTo(1L);
        Regional outra = new Regional().setId(2L).setAtivo(true);
        when(regionais.findById(2L)).thenReturn(Optional.of(outra));
        assertThat(service.updateCar(10L, new UpdateCarRequest(null, null, null, 2L))
                .regional().id()).isEqualTo(2L);
    }

    @Test
    void administradorEscolheRegionalAtivaParaCadastrar() {
        gestor.setPerfil(Perfil.ADMINISTRADOR).setRegional(null);
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.createCar(new CreateCarRequest("ABC1234", "Civic", null)));
        when(regionais.findById(1L)).thenReturn(Optional.of(regional.setAtivo(false)));
        assertThrows(RegionalNaoPermitidaException.class,
                () -> service.createCar(new CreateCarRequest("ABC1234", "Civic", 1L)));
        regional.setAtivo(true);
        when(cars.save(any())).thenAnswer(call -> ((Car) call.getArgument(0)).setActive(true));
        assertThat(service.createCar(new CreateCarRequest("ABC1234", "Civic", 1L))
                .regional().id()).isEqualTo(1L);
    }

    @Test
    void colaboradorNaoFazCrudMasContinuaSelecionandoVeiculosAtivosParaOcorrencias() {
        gestor.setPerfil(Perfil.COLABORADOR);
        assertThrows(PerfilNaoPermitidoException.class, () -> service.createCar(null));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.updateCar(1L, null));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.deleteCar(1L));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.getCarById(1L));
        assertThrows(PerfilNaoPermitidoException.class,
                () -> service.getCarsByFilters(null, null, PageRequest.of(0, 10)));
        when(cars.findByFilters(null, true, null, Pageable.unpaged())).thenReturn(Page.empty());
        assertThat(service.getAvailableCars()).isEmpty();
    }

    @Test
    void gestorNaoPodeAlterarPostosMesmoChamandoServicoDiretamente() {
        GasStationService postos = new GasStationService(null, null, null, autorizacao, null);
        assertThrows(PerfilNaoPermitidoException.class, () -> postos.create(null));
        assertThrows(PerfilNaoPermitidoException.class, () -> postos.update(1L, null));
        assertThrows(PerfilNaoPermitidoException.class, () -> postos.deleteGasStation(1L));
    }
}
