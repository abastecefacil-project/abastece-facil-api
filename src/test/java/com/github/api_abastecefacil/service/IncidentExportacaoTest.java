package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.Incident;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.IncidentRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.github.api_abastecefacil.util.PlanilhaTeste.abrir;
import static com.github.api_abastecefacil.util.PlanilhaTeste.linha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncidentExportacaoTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final UsuarioAutenticadoProvider autenticado = mock(UsuarioAutenticadoProvider.class);
    private final IncidentService service = new IncidentService(
            null, incidents, null, new AutorizacaoOperacional(autenticado));

    @Test
    void administradorExportaComCabecalhoELinhas() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(incidents.findByFilters(null, null, null, null, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(new Incident()
                        .setOccurrenceDate(LocalDate.of(2026, 8, 18)).setCarPlate("ABC1234")
                        .setTitle("Sujo internamente").setDescription("Bancos sujos").setUserName("Fulano")
                        .setCreatedAt(LocalDateTime.of(2026, 8, 19, 9, 0)))));

        try (Workbook planilha = abrir(service.exportarOcorrencias(null, null, null, null))) {
            Sheet aba = planilha.getSheetAt(0);
            assertThat(aba.getLastRowNum()).isEqualTo(1);
            assertThat(linha(aba, 0)).containsExactly(
                    "Data da ocorrência", "Placa", "Título", "Descrição", "Registrado por", "Registrado em");
            assertThat(linha(aba, 1)).containsExactly(
                    "18/08/2026", "ABC1234", "Sujo internamente", "Bancos sujos", "Fulano", "19/08/2026");
        }
    }

    @Test
    void gestorExportaSemEscopoDeRegional() {
        // Ocorrência não tem regional: o gestor exporta o mesmo que vê na listagem.
        autenticar(new User().setPerfil(Perfil.GESTOR_FROTA).setRegional(new Regional().setId(1L)));
        when(incidents.findByFilters(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.exportarOcorrencias(null, null, null, null);

        verify(incidents).findByFilters(null, null, null, null, Pageable.unpaged());
    }

    @Test
    void colaboradorNaoExporta() {
        autenticar(new User().setPerfil(Perfil.COLABORADOR));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.exportarOcorrencias(null, null, null, null));
        verifyNoInteractions(incidents);
    }

    @Test
    void filtrosChegamAoRepositorioSemPaginacao() throws Exception {
        autenticar(new User().setPerfil(Perfil.ADMINISTRADOR));
        when(incidents.findByFilters(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        LocalDate data = LocalDate.of(2026, 8, 18);

        try (Workbook planilha = abrir(service.exportarOcorrencias("ABC1234", "sujo", "fulano", data))) {
            assertThat(planilha.getSheetAt(0).getLastRowNum()).isZero();
        }
        verify(incidents).findByFilters("ABC1234", "sujo", "fulano", data, Pageable.unpaged());
    }

    private void autenticar(User usuario) {
        when(autenticado.obterUsuarioAutenticado()).thenReturn(usuario);
    }
}
