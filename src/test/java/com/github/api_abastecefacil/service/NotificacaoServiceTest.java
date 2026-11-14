package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.notificacao.NotificacaoResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.Notificacao;
import com.github.api_abastecefacil.model.TipoNotificacao;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.NotificacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {
    @Mock NotificacaoRepository notificacoes;
    @Mock AutorizacaoOperacional autorizacao;
    @InjectMocks NotificacaoService service;

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
    void mapeiaOsDoisTiposComCamposQueNaoSeAplicamNulos() {
        when(autorizacao.autorizarAdministracao()).thenReturn(new User().setId(5L));
        var page = PageRequest.of(0, 10);
        var criadoEm = LocalDateTime.of(2026, 11, 14, 10, 0);
        var recusa = notificacao(Map.of("id", 1L, "tipo", TipoNotificacao.RECUSA_NEO, "usuarioNome", "Ana",
                "postoId", 7L, "postoNome", "Posto Centro", "criadoEm", criadoEm));
        var ocorrencia = notificacao(Map.of("id", 2L, "tipo", TipoNotificacao.OCORRENCIA, "usuarioNome", "Bruno",
                "ocorrenciaId", 9L, "ocorrenciaPlaca", "MGM5H18", "ocorrenciaTitulo", "Sujo", "criadoEm", criadoEm,
                "lida", true));
        when(notificacoes.findByAdministradorIdOrderByCriadoEmDescIdDesc(5L, page))
                .thenReturn(new PageImpl<>(List.of(recusa, ocorrencia), page, 2));

        var itens = service.listar(page).pagina().getContent();

        assertThat(itens.get(0)).isEqualTo(new NotificacaoResponse(1L, TipoNotificacao.RECUSA_NEO, false, criadoEm,
                "Ana", 7L, "Posto Centro", null, null, null));
        assertThat(itens.get(1)).isEqualTo(new NotificacaoResponse(2L, TipoNotificacao.OCORRENCIA, true, criadoEm,
                "Bruno", null, null, 9L, "MGM5H18", "Sujo"));
    }

    private static Notificacao notificacao(Map<String, Object> campos) {
        var n = new Notificacao();
        campos.forEach((campo, valor) -> ReflectionTestUtils.setField(n, campo, valor));
        return n;
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
