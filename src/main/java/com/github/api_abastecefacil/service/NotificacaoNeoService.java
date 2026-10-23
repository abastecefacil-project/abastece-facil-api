package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.NotificacaoNeoResponse;
import com.github.api_abastecefacil.dto.gasStation.NotificacoesNeoResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.repository.NotificacaoNeoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificacaoNeoService {
    private final NotificacaoNeoRepository notificacoes;
    private final AutorizacaoOperacional autorizacao;

    public NotificacaoNeoService(NotificacaoNeoRepository notificacoes, AutorizacaoOperacional autorizacao) {
        this.notificacoes = notificacoes;
        this.autorizacao = autorizacao;
    }

    public NotificacoesNeoResponse listar(Pageable pageable) {
        Long administradorId = autorizacao.autorizarAdministracao().getId();
        var pagina = notificacoes.findByAdministradorIdOrderByCriadoEmDescIdDesc(administradorId, pageable)
                .map(n -> new NotificacaoNeoResponse(n.getId(), n.getPostoId(), n.getUsuarioNome(),
                        n.getPostoNome(), n.getCriadoEm(), n.isLida()));
        return new NotificacoesNeoResponse(pagina, notificacoes.countByAdministradorIdAndLidaFalse(administradorId));
    }

    @Transactional
    public void marcarLida(Long id) {
        Long administradorId = autorizacao.autorizarAdministracao().getId();
        if (notificacoes.marcarLida(id, administradorId) == 0) {
            throw new NotFoundException("Notificação não encontrada");
        }
    }
}
