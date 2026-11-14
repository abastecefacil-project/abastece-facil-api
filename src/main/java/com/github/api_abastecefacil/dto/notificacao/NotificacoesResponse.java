package com.github.api_abastecefacil.dto.notificacao;

import org.springframework.data.domain.Page;

public record NotificacoesResponse(Page<NotificacaoResponse> pagina, long naoLidas) {}
