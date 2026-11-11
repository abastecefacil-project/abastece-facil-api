package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.exception.RegionalNaoPermitidaException;
import com.github.api_abastecefacil.mapper.CarMapper;
import com.github.api_abastecefacil.mapper.RegionalMapper;
import com.github.api_abastecefacil.model.*;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.repository.RegionalRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class CarExportacaoTest {
    private final CarRepository cars = mock(CarRepository.class);
    private final UsuarioAutenticadoProvider autenticado = mock(UsuarioAutenticadoProvider.class);
    private final CarService service = new CarService(cars, new CarMapper(new RegionalMapper()),
            new AutorizacaoOperacional(autenticado), mock(RegionalRepository.class), autenticado);
    private final Regional joinville = new Regional().setId(1L).setNome("Joinville").setAtivo(true);

    @Test
    void administradorExportaTodasAsRegionais() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(cars.findByFilters(isNull(), isNull(), isNull(), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(
                        veiculo("ABC1234", "Civic", joinville, true),
                        veiculo("XYZ9876", "Gol", null, false))));

        try (Workbook planilha = abrir(service.exportarVeiculos(null, null))) {
            Sheet aba = planilha.getSheetAt(0);
            assertThat(aba.getLastRowNum()).isEqualTo(2);
            assertThat(linha(aba, 0)).containsExactly(
                    "Placa", "Modelo", "Regional", "Situação", "Cadastrado em", "Atualizado em");
            assertThat(aba.getRow(0).getCell(0).getCellStyle().getFontIndex()).isNotZero();
            assertThat(linha(aba, 1)).containsExactly("ABC1234", "Civic", "Joinville", "Ativo", "05/03/2026", "");
            assertThat(linha(aba, 2)).containsExactly("XYZ9876", "Gol", "", "Inativo", "05/03/2026", "");
        }
    }

    @Test
    void gestorExportaSoASuaRegional() throws Exception {
        autenticar(new User().setPerfil(Perfil.GESTOR_FROTA).setRegional(joinville));
        when(cars.findByFilters(any(), any(), eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(veiculo("ABC1234", "Civic", joinville, true))));

        try (Workbook planilha = abrir(service.exportarVeiculos(null, null))) {
            assertThat(planilha.getSheetAt(0).getLastRowNum()).isEqualTo(1);
        }
        verify(cars).findByFilters(null, null, 1L, Pageable.unpaged());
    }

    @Test
    void gestorSemRegionalNaoExporta() {
        autenticar(new User().setPerfil(Perfil.GESTOR_FROTA));
        assertThrows(RegionalNaoPermitidaException.class, () -> service.exportarVeiculos(null, null));
        verifyNoInteractions(cars);
    }

    @Test
    void colaboradorNaoExporta() {
        autenticar(new User().setPerfil(Perfil.COLABORADOR));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.exportarVeiculos(null, null));
        verifyNoInteractions(cars);
    }

    @Test
    void filtrosChegamAoRepositorioSemPaginacao() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(cars.findByFilters(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        try (Workbook planilha = abrir(service.exportarVeiculos("civic", false))) {
            assertThat(planilha.getSheetAt(0).getLastRowNum()).isZero();
        }
        verify(cars).findByFilters("civic", false, null, Pageable.unpaged());
    }

    private void autenticar(User usuario) {
        when(autenticado.obterUsuarioAutenticado()).thenReturn(usuario);
    }

    private static Car veiculo(String placa, String modelo, Regional regional, boolean ativo) {
        return new Car().setLicensePlate(placa).setModel(modelo).setRegional(regional).setActive(ativo)
                .setCreatedAt(LocalDateTime.of(2026, 3, 5, 14, 30));
    }

    private static Workbook abrir(byte[] conteudo) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(conteudo));
    }

    private static List<String> linha(Sheet aba, int indice) {
        var formatador = new org.apache.poi.ss.usermodel.DataFormatter();
        return java.util.stream.IntStream.range(0, 6)
                .mapToObj(coluna -> formatador.formatCellValue(aba.getRow(indice).getCell(coluna)))
                .toList();
    }
}
