package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.notificacao.NotificacaoResponse;
import com.github.api_abastecefacil.dto.notificacao.NotificacoesResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.repository.NotificacaoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificacaoService {
    private final NotificacaoRepository notificacoes;
    private final AutorizacaoOperacional autorizacao;

    public NotificacaoService(NotificacaoRepository notificacoes, AutorizacaoOperacional autorizacao) {
        this.notificacoes = notificacoes;
        this.autorizacao = autorizacao;
    }

    public NotificacoesResponse listar(Pageable pageable) {
        Long administradorId = autorizacao.autorizarAdministracao().getId();
        var pagina = notificacoes.findByAdministradorIdOrderByCriadoEmDescIdDesc(administradorId, pageable)
                .map(n -> new NotificacaoResponse(n.getId(), n.getPostoId(), n.getUsuarioNome(),
                        n.getPostoNome(), n.getCriadoEm(), n.isLida()));
        return new NotificacoesResponse(pagina, notificacoes.countByAdministradorIdAndLidaFalse(administradorId));
    }

    @Transactional
    public void marcarLida(Long id) {
        Long administradorId = autorizacao.autorizarAdministracao().getId();
        if (notificacoes.marcarLida(id, administradorId) == 0) {
            throw new NotFoundException("Notificação não encontrada");
        }
    }
}
