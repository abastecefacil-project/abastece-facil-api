package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.*;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.repository.AvaliacaoPostoRepository;
import com.github.api_abastecefacil.repository.GasStationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AvaliacaoPostoService {
    private final AvaliacaoPostoRepository avaliacoes;
    private final GasStationRepository postos;
    private final UsuarioAutenticadoProvider usuarios;

    public AvaliacaoPostoService(AvaliacaoPostoRepository avaliacoes, GasStationRepository postos,
                                UsuarioAutenticadoProvider usuarios) {
        this.avaliacoes = avaliacoes;
        this.postos = postos;
        this.usuarios = usuarios;
    }

    public List<ResumoAvaliacaoPosto> resumos() {
        return avaliacoes.resumos();
    }

    public AvaliacoesPostoResponse consultar(Long postoId, int pagina) {
        validarPosto(postoId);
        return resposta(postoId, usuarios.obterUsuarioAutenticado().getId(), pagina);
    }

    @Transactional
    public AvaliacoesPostoResponse salvar(Long postoId, AvaliacaoPostoRequest request) {
        validarPosto(postoId);
        Long usuarioId = usuarios.obterUsuarioAutenticado().getId();
        avaliacoes.salvar(postoId, usuarioId, request.nota().intValueExact(),
                request.comentario() == null ? null : request.comentario().strip());
        return resposta(postoId, usuarioId, 0);
    }

    @Transactional
    public AvaliacoesPostoResponse remover(Long postoId) {
        validarPosto(postoId);
        Long usuarioId = usuarios.obterUsuarioAutenticado().getId();
        avaliacoes.remover(postoId, usuarioId);
        return resposta(postoId, usuarioId, 0);
    }

    private AvaliacoesPostoResponse resposta(Long postoId, Long usuarioId, int pagina) {
        var resumo = avaliacoes.resumo(postoId);
        return new AvaliacoesPostoResponse(resumo.getMedia(), resumo.getTotal(),
                avaliacoes.buscarMinha(postoId, usuarioId).orElse(null),
                avaliacoes.listar(postoId, PageRequest.of(Math.max(0, pagina), 10)));
    }

    private void validarPosto(Long postoId) {
        if (!postos.existsById(postoId)) throw new NotFoundException("Posto não encontrado");
    }
}
