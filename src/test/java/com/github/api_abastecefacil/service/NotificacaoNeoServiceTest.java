package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.NotificacaoNeoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoNeoServiceTest {
    @Mock NotificacaoNeoRepository notificacoes;
    @Mock AutorizacaoOperacional autorizacao;
    @InjectMocks NotificacaoNeoService service;

    @Test
    void consultaUsaSomenteDestinatarioAutenticado() {
        when(autorizacao.autorizarAdministracao()).thenReturn(new User().setId(5L));
        var page = PageRequest.of(0, 10);
        when(notificacoes.findByAdministradorIdOrderByCriadoEmDescIdDesc(5L, page)).thenReturn(Page.empty(page));
        when(notificacoes.countByAdministradorIdAndLidaFalse(5L)).thenReturn(3L);
        assertThat(service.listar(page).naoLidas()).isEqualTo(3);
        verify(notificacoes).findByAdministradorIdOrderByCriadoEmDescIdDesc(5L, page);
    }

    @Test
    void naoPermiteLerNotificacaoDeOutroAdministrador() {
        when(autorizacao.autorizarAdministracao()).thenReturn(new User().setId(5L));
        when(notificacoes.marcarLida(10L, 5L)).thenReturn(0);
        assertThrows(NotFoundException.class, () -> service.marcarLida(10L));
    }

    @Test
    void leituraEPaginacaoExigemAdministrador() {
        when(autorizacao.autorizarAdministracao()).thenThrow(new PerfilNaoPermitidoException("Sem permissão"));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.listar(PageRequest.of(0, 10)));
        assertThrows(PerfilNaoPermitidoException.class, () -> service.marcarLida(10L));
        verifyNoInteractions(notificacoes);
    }
}
