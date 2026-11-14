package com.github.api_abastecefacil.dto.notificacao;

import com.github.api_abastecefacil.model.TipoNotificacao;

import java.time.LocalDateTime;

/** Formato plano para os dois tipos: campos que não se aplicam ao tipo vêm null. */
public record NotificacaoResponse(Long id, TipoNotificacao tipo, boolean lida, LocalDateTime criadoEm,
                                  String usuarioNome, Long postoId, String postoNome,
                                  Long ocorrenciaId, String ocorrenciaPlaca, String ocorrenciaTitulo) {}
