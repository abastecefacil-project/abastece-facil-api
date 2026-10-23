package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.repository.NotificacaoNeoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliacaoNeoServiceTest {
    @Mock GasStationRepository postos;
    @Mock UsuarioAutenticadoProvider usuarios;
    @Mock NotificacaoNeoRepository notificacoes;
    @InjectMocks AvaliacaoNeoService service;
    User usuario;

    @BeforeEach
    void preparar() {
        usuario = new User().setId(7L).setPerfil(Perfil.COLABORADOR);
        when(postos.existsById(1L)).thenReturn(true);
    }

    @Test
    void salvaNaoPassouComIdentidadeDaSessao() {
        when(usuarios.obterUsuarioAutenticado()).thenReturn(usuario);
        when(postos.salvarAvaliacaoNeo(1L, 7L, false)).thenReturn(1);
        service.salvar(1L, false);
        verify(postos).salvarAvaliacaoNeo(1L, 7L, false);
        verify(notificacoes).notificarRecusa(1L, 7L);
    }

    @Test
    void naoNotificaRespostaNegativaRepetidaNemRespostaPositiva() {
        when(usuarios.obterUsuarioAutenticado()).thenReturn(usuario);
        when(postos.salvarAvaliacaoNeo(1L, 7L, false)).thenReturn(0);
        when(postos.salvarAvaliacaoNeo(1L, 7L, true)).thenReturn(1);
        service.salvar(1L, false);
        service.salvar(1L, true);
        verifyNoInteractions(notificacoes);
    }

    @Test
    void colaboradorVePropriaRespostaETotaisComDataSemExporIdentidades() {
        when(usuarios.obterUsuarioAutenticado()).thenReturn(usuario);
        when(postos.buscarAvaliacaoNeo(1L, 7L)).thenReturn(Optional.of(false));
        when(postos.contarAvaliacoesNeo(1L, true)).thenReturn(3L);
        when(postos.contarAvaliacoesNeo(1L, false)).thenReturn(2L);
        var data = LocalDateTime.of(2026, 10, 19, 12, 30);
        when(postos.buscarUltimoRelatoNeo(1L)).thenReturn(Optional.of(data));
        var resposta = service.consultar(1L);
        assertThat(resposta.minhaResposta()).isFalse();
        assertThat(resposta.passaram()).isEqualTo(3);
        assertThat(resposta.naoPassaram()).isEqualTo(2);
        assertThat(resposta.ultimoRelatoEm()).isEqualTo(data);
    }

    @Test
    void administradorVeTotaisSemExporIdentidades() {
        usuario.setPerfil(Perfil.ADMINISTRADOR);
        when(usuarios.obterUsuarioAutenticado()).thenReturn(usuario);
        when(postos.buscarAvaliacaoNeo(1L, 7L)).thenReturn(Optional.empty());
        when(postos.contarAvaliacoesNeo(1L, true)).thenReturn(3L);
        when(postos.contarAvaliacoesNeo(1L, false)).thenReturn(2L);
        var resposta = service.consultar(1L);
        assertThat(resposta.minhaResposta()).isNull();
        assertThat(resposta.passaram()).isEqualTo(3);
        assertThat(resposta.naoPassaram()).isEqualTo(2);
    }

    @Test
    void postoSemRespostasRetornaZerosSemData() {
        when(usuarios.obterUsuarioAutenticado()).thenReturn(usuario);
        var resposta = service.consultar(1L);
        assertThat(resposta.minhaResposta()).isNull();
        assertThat(resposta.passaram()).isZero();
        assertThat(resposta.naoPassaram()).isZero();
        assertThat(resposta.ultimoRelatoEm()).isNull();
    }

    @Test
    void removeSomenteRespostaDoUsuarioLogadoERetornaResumoAtualizado() {
        when(usuarios.obterUsuarioAutenticado()).thenReturn(usuario);
        when(postos.contarAvaliacoesNeo(1L, true)).thenReturn(2L);
        var resposta = service.remover(1L);
        verify(postos).removerAvaliacaoNeo(1L, 7L);
        assertThat(resposta.minhaResposta()).isNull();
        assertThat(resposta.passaram()).isEqualTo(2);
    }

    @Test
    void naoPermiteResponderOuConsultarPostoInexistente() {
        when(postos.existsById(1L)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> service.salvar(1L, true));
        assertThrows(NotFoundException.class, () -> service.consultar(1L));
        assertThrows(NotFoundException.class, () -> service.remover(1L));
        verifyNoInteractions(usuarios);
        verify(postos, never()).salvarAvaliacaoNeo(anyLong(), anyLong(), anyBoolean());
        verify(postos, never()).removerAvaliacaoNeo(anyLong(), anyLong());
    }
}
