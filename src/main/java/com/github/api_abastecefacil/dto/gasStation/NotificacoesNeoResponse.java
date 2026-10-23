package com.github.api_abastecefacil.dto.gasStation;

import org.springframework.data.domain.Page;

public record NotificacoesNeoResponse(Page<NotificacaoNeoResponse> pagina, long naoLidas) {}
