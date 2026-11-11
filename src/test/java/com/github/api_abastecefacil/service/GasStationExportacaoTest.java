package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.GasStationRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.github.api_abastecefacil.util.PlanilhaTeste.abrir;
import static com.github.api_abastecefacil.util.PlanilhaTeste.linha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GasStationExportacaoTest {
    private final GasStationRepository postos = mock(GasStationRepository.class);
    private final UsuarioAutenticadoProvider autenticado = mock(UsuarioAutenticadoProvider.class);
    private final GasStationService service = new GasStationService(
            postos, null, null, new AutorizacaoOperacional(autenticado), null);

    @Test
    void administradorExportaComCabecalhoELinhas() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(postos.findByFilters(null, null, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(posto())));

        try (Workbook planilha = abrir(service.exportarPostos(null, null))) {
            Sheet aba = planilha.getSheetAt(0);
            assertThat(aba.getLastRowNum()).isEqualTo(1);
            assertThat(linha(aba, 0)).containsExactly(
                    "Nome", "Nome fantasia", "CNPJ", "Endereço", "Bairro", "Cidade", "UF", "CEP", "Telefone",
                    "Horário de funcionamento", "Latitude", "Longitude", "Situação", "Cadastrado em",
                    "Atualizado em");
            assertThat(linha(aba, 1)).containsExactly(
                    "POSTO Z21 LTDA", "POSTO ZANDONA 21", "01234567000190", "Rua A, 622", "Centro",
                    "JOINVILLE", "SC", "09201000", "4733334444", "24h", "-26.30444400", "-48.84638300",
                    "Inativo", "05/03/2026", "");
        }
    }

    @Test
    void cnpjCepETelefoneSaemComoTexto() throws Exception {
        // Só dígitos e com zero à esquerda: como número, o Excel perderia o zero.
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(postos.findByFilters(any(), any(), any())).thenReturn(new PageImpl<>(List.of(posto())));

        try (Workbook planilha = abrir(service.exportarPostos(null, null))) {
            Sheet aba = planilha.getSheetAt(0);
            for (int coluna : new int[]{2, 7, 8}) {
                assertThat(aba.getRow(1).getCell(coluna).getCellType()).isEqualTo(CellType.STRING);
            }
        }
    }

    @Test
    void gestorExportaTodos() {
        // Posto não tem regional: o gestor exporta o mesmo que vê na listagem.
        autenticar(new User().setPerfil(Perfil.GESTOR_FROTA));
        when(postos.findByFilters(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.exportarPostos(null, null);

        verify(postos).findByFilters(null, null, Pageable.unpaged());
    }

    @Test
    void colaboradorNaoExporta() {
        autenticar(new User().setPerfil(Perfil.COLABORADOR));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.exportarPostos(null, null));
        verifyNoInteractions(postos);
    }

    @Test
    void filtrosChegamAoRepositorioSemPaginacao() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(postos.findByFilters(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        try (Workbook planilha = abrir(service.exportarPostos("zandona", true))) {
            assertThat(planilha.getSheetAt(0).getLastRowNum()).isZero();
        }
        verify(postos).findByFilters("zandona", true, Pageable.unpaged());
    }

    private void autenticar(User usuario) {
        when(autenticado.obterUsuarioAutenticado()).thenReturn(usuario);
    }

    private static GasStation posto() {
        return new GasStation().setName("POSTO Z21 LTDA").setFantasyName("POSTO ZANDONA 21")
                .setCnpj("01234567000190").setAddress("Rua A, 622").setDistrict("Centro").setCity("JOINVILLE")
                .setState("SC").setCep("09201000").setPhone("4733334444").setBusinessHours("24h")
                .setLatitude(new BigDecimal("-26.30444400")).setLongitude(new BigDecimal("-48.84638300"))
                .setActive(false).setCreatedAt(LocalDateTime.of(2026, 3, 5, 14, 30));
    }
}
