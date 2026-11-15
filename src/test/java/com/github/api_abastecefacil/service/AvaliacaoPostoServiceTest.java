package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.*;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliacaoPostoServiceTest {
    @Mock AvaliacaoPostoRepository avaliacoes;
    @Mock GasStationRepository postos;
    @Mock UsuarioAutenticadoProvider usuarios;
    @InjectMocks AvaliacaoPostoService service;

    private void preparar() {
        when(postos.existsById(1L)).thenReturn(true);
        when(usuarios.obterUsuarioAutenticado()).thenReturn(new User().setId(7L));
        var resumo = mock(ResumoAvaliacaoPosto.class);
        when(resumo.getMedia()).thenReturn(null);
        when(avaliacoes.resumo(1L)).thenReturn(resumo);
        when(avaliacoes.buscarMinha(1L, 7L)).thenReturn(Optional.empty());
        when(avaliacoes.listar(eq(1L), any())).thenReturn(Page.empty());
    }

    @Test
    void salvaComAutorDaSessaoELimpaEspacosDoComentario() {
        preparar();
        service.salvar(1L, new AvaliacaoPostoRequest(BigDecimal.valueOf(5), "  Ótimo atendimento!  "));
        verify(avaliacoes).salvar(1L, 7L, 5, "Ótimo atendimento!");
        verify(avaliacoes).listar(1L, PageRequest.of(0, 10));
    }

    @Test
    void permiteNotaSemComentario() {
        preparar();
        service.salvar(1L, new AvaliacaoPostoRequest(BigDecimal.valueOf(1), null));
        verify(avaliacoes).salvar(1L, 7L, 1, null);
    }

    @Test
    void removeSomenteAvaliacaoDoUsuarioAutenticado() {
        preparar();
        var response = service.remover(1L);
        verify(avaliacoes).remover(1L, 7L);
        assertThat(response.minhaAvaliacao()).isNull();
        assertThat(response.media()).isNull();
        assertThat(response.total()).isZero();
    }

    @Test
    void paginaAListaSemPerderAPropriaAvaliacao() {
        preparar();
        var minha = mock(AvaliacaoPosto.class);
        when(avaliacoes.buscarMinha(1L, 7L)).thenReturn(Optional.of(minha));
        assertThat(service.consultar(1L, 3).minhaAvaliacao()).isSameAs(minha);
        verify(avaliacoes).listar(1L, PageRequest.of(3, 10));
    }

    @Test
    void rejeitaPostoInexistenteAntesDeGravarOuRemover() {
        assertThatThrownBy(() -> service.salvar(99L, new AvaliacaoPostoRequest(BigDecimal.valueOf(5), null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.remover(99L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(avaliacoes, usuarios);
    }
}
