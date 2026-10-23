package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.AvaliacaoNeoResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.repository.NotificacaoNeoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvaliacaoNeoService {
    private final GasStationRepository postos;
    private final UsuarioAutenticadoProvider usuarios;
    private final NotificacaoNeoRepository notificacoes;

    public AvaliacaoNeoService(GasStationRepository postos, UsuarioAutenticadoProvider usuarios,
                              NotificacaoNeoRepository notificacoes) {
        this.postos = postos;
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
    }

    public AvaliacaoNeoResponse consultar(Long postoId) {
        validarPosto(postoId);
        var usuario = usuarios.obterUsuarioAutenticado();
        return resumo(postoId, usuario.getId());
    }

    @Transactional
    public AvaliacaoNeoResponse salvar(Long postoId, boolean passouNeo) {
        validarPosto(postoId);
        var usuario = usuarios.obterUsuarioAutenticado();
        // A identidade vem da sessão, nunca de um id enviado pelo cliente.
        // O upsert retorna zero para respostas iguais, inclusive em envios simultâneos.
        int alteradas = postos.salvarAvaliacaoNeo(postoId, usuario.getId(), passouNeo);
        if (!passouNeo && alteradas > 0) notificacoes.notificarRecusa(postoId, usuario.getId());
        return resumo(postoId, usuario.getId());
    }

    @Transactional
    public AvaliacaoNeoResponse remover(Long postoId) {
        validarPosto(postoId);
        var usuario = usuarios.obterUsuarioAutenticado();
        postos.removerAvaliacaoNeo(postoId, usuario.getId());
        return resumo(postoId, usuario.getId());
    }

    private AvaliacaoNeoResponse resumo(Long postoId, Long usuarioId) {
        return new AvaliacaoNeoResponse(postos.buscarAvaliacaoNeo(postoId, usuarioId).orElse(null),
                postos.contarAvaliacoesNeo(postoId, true),
                postos.contarAvaliacoesNeo(postoId, false),
                postos.buscarUltimoRelatoNeo(postoId).orElse(null));
    }

    private void validarPosto(Long postoId) {
        if (!postos.existsById(postoId)) throw new NotFoundException("Posto não encontrado");
    }
}
