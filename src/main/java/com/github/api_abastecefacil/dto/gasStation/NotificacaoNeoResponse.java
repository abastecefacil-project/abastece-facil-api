package com.github.api_abastecefacil.dto.gasStation;

import java.time.LocalDateTime;

public record NotificacaoNeoResponse(Long id, Long postoId, String usuarioNome, String postoNome,
                                     LocalDateTime criadoEm, boolean lida) {}
