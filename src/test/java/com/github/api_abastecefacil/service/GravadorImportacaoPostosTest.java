package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GravadorImportacaoPostosTest {

    private static final Long ID = 42L;
    private static final BigDecimal LAT_ANTIGA = new BigDecimal("-26.30000000");
    private static final BigDecimal LON_ANTIGA = new BigDecimal("-48.84000000");
    private static final Coordinates COORDENADAS_NOVAS =
            new Coordinates(new BigDecimal("-26.25000000"), new BigDecimal("-48.80000000"));

    @Mock
    private GasStationRepository gasStationRepository;

    private GravadorImportacaoPostos gravador;

    @BeforeEach
    void setUp() {
        gravador = new GravadorImportacaoPostos(gasStationRepository);
    }

    private static LinhaPlanilhaPosto linhaCompleta() {
        return new LinhaPlanilhaPosto(5, "12345678000195", "12.345.678/0001-95", "Posto Novo Ltda",
                "Posto Novo", "(47) 3422-9999", "89202-000", "Rua Nova, 500", "América", "Joinville", "SC",
                "06:00 - 22:00", List.of());
    }

    /** Linha com os três campos opcionais nulos, como chegam de célula vazia. */
    private static LinhaPlanilhaPosto linhaComNulos() {
        return new LinhaPlanilhaPosto(5, "12345678000195", "12.345.678/0001-95", "Posto Novo Ltda",
                null, null, "89202-000", "Rua Nova, 500", "América", "Joinville", "SC", null, List.of());
    }

    private static GasStation postoNoBanco(boolean ativo) {
        return new GasStation()
                .setId(ID)
                .setName("Posto Antigo Ltda")
                .setFantasyName("Posto Antigo")
                .setCnpj("12345678000195")
                .setPhone("(47) 3422-1111")
                .setCep("89201-250")
                .setAddress("Rua Velha, 100")
                .setDistrict("Centro")
                .setCity("Joinville")
                .setState("SC")
                .setBusinessHours("07:00 - 19:00")
                .setLatitude(LAT_ANTIGA)
                .setLongitude(LON_ANTIGA)
                .setActive(ativo);
    }

    private GasStation salvo() {
        ArgumentCaptor<GasStation> captor = ArgumentCaptor.forClass(GasStation.class);
        verify(gasStationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void inserir_ShouldBuildEntityWithAllFieldsMaskedCnpjAndCoordinates() {
        gravador.inserir(linhaCompleta(), COORDENADAS_NOVAS);

        GasStation posto = salvo();
        assertThat(posto.getId()).isNull();
        assertThat(posto.getName()).isEqualTo("Posto Novo Ltda");
        assertThat(posto.getFantasyName()).isEqualTo("Posto Novo");
        assertThat(posto.getCnpj()).isEqualTo("12.345.678/0001-95");
        assertThat(posto.getPhone()).isEqualTo("(47) 3422-9999");
        assertThat(posto.getCep()).isEqualTo("89202-000");
        assertThat(posto.getAddress()).isEqualTo("Rua Nova, 500");
        assertThat(posto.getDistrict()).isEqualTo("América");
        assertThat(posto.getCity()).isEqualTo("Joinville");
        assertThat(posto.getState()).isEqualTo("SC");
        assertThat(posto.getBusinessHours()).isEqualTo("06:00 - 22:00");
        assertThat(posto.getLatitude()).isEqualTo(COORDENADAS_NOVAS.latitude());
        assertThat(posto.getLongitude()).isEqualTo(COORDENADAS_NOVAS.longitude());
    }

    @Test
    void atualizar_ShouldOverwriteNonNullFields_AndKeepCoordinates() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(true)));

        gravador.atualizar(ID, linhaCompleta(), false);

        GasStation posto = salvo();
        assertThat(posto.getName()).isEqualTo("Posto Novo Ltda");
        assertThat(posto.getCnpj()).isEqualTo("12.345.678/0001-95");
        assertThat(posto.getAddress()).isEqualTo("Rua Nova, 500");
        assertThat(posto.getLatitude()).isEqualTo(LAT_ANTIGA);
        assertThat(posto.getLongitude()).isEqualTo(LON_ANTIGA);
        assertThat(posto.getActive()).isTrue();
    }

    @Test
    void atualizar_ShouldNotOverwriteDatabase_WhenFieldIsNullInTheSpreadsheet() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(true)));

        gravador.atualizar(ID, linhaComNulos(), false);

        GasStation posto = salvo();
        assertThat(posto.getFantasyName()).isEqualTo("Posto Antigo");
        assertThat(posto.getPhone()).isEqualTo("(47) 3422-1111");
        assertThat(posto.getBusinessHours()).isEqualTo("07:00 - 19:00");
        assertThat(posto.getName()).isEqualTo("Posto Novo Ltda");
    }

    @Test
    void atualizarComCoordenadas_ShouldWriteAddressAndNewCoordinates() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(true)));

        gravador.atualizarComCoordenadas(ID, linhaCompleta(), COORDENADAS_NOVAS, false);

        GasStation posto = salvo();
        assertThat(posto.getAddress()).isEqualTo("Rua Nova, 500");
        assertThat(posto.getCep()).isEqualTo("89202-000");
        assertThat(posto.getLatitude()).isEqualTo(COORDENADAS_NOVAS.latitude());
        assertThat(posto.getLongitude()).isEqualTo(COORDENADAS_NOVAS.longitude());
    }

    @Test
    void atualizarSemEndereco_ShouldWriteNewPhone_AndKeepAddressAndCoordinates() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(true)));

        gravador.atualizarSemEndereco(ID, linhaCompleta(), false);

        GasStation posto = salvo();
        assertThat(posto.getPhone()).isEqualTo("(47) 3422-9999");
        assertThat(posto.getName()).isEqualTo("Posto Novo Ltda");
        assertThat(posto.getFantasyName()).isEqualTo("Posto Novo");
        assertThat(posto.getBusinessHours()).isEqualTo("06:00 - 22:00");
        assertThat(posto.getCnpj()).isEqualTo("12.345.678/0001-95");
        assertThat(posto.getAddress()).isEqualTo("Rua Velha, 100");
        assertThat(posto.getDistrict()).isEqualTo("Centro");
        assertThat(posto.getCity()).isEqualTo("Joinville");
        assertThat(posto.getState()).isEqualTo("SC");
        assertThat(posto.getCep()).isEqualTo("89201-250");
        assertThat(posto.getLatitude()).isEqualTo(LAT_ANTIGA);
        assertThat(posto.getLongitude()).isEqualTo(LON_ANTIGA);
    }

    @Test
    void atualizar_ShouldSetActiveTrue_WhenReactivating() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(false)));

        gravador.atualizar(ID, linhaCompleta(), true);

        assertThat(salvo().getActive()).isTrue();
    }

    @Test
    void atualizarSemEndereco_ShouldSetActiveTrue_WhenReactivating() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(false)));

        gravador.atualizarSemEndereco(ID, linhaCompleta(), true);

        assertThat(salvo().getActive()).isTrue();
    }

    @Test
    void atualizar_ShouldNotTouchActive_WhenNotReactivating() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(false)));

        gravador.atualizar(ID, linhaCompleta(), false);

        assertThat(salvo().getActive()).isFalse();
    }

    @Test
    void desativar_ShouldSetActiveFalse_AndNeverDelete() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.of(postoNoBanco(true)));

        gravador.desativar(ID);

        assertThat(salvo().getActive()).isFalse();
        verify(gasStationRepository, never()).delete(any());
        verify(gasStationRepository, never()).deleteById(any());
    }

    @Test
    void atualizar_ShouldThrowNotFound_WhenStationNoLongerExists() {
        when(gasStationRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gravador.atualizar(ID, linhaCompleta(), false))
                .isInstanceOf(NotFoundException.class);
        verify(gasStationRepository, never()).save(any());
    }
}
