package com.github.api_abastecefacil.dto.notificacao;

import java.time.LocalDateTime;

public record NotificacaoResponse(Long id, Long postoId, String usuarioNome, String postoNome,
                                  LocalDateTime criadoEm, boolean lida) {}
